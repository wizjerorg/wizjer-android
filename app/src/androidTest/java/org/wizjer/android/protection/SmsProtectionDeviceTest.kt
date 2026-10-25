package org.wizjer.android.protection

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An incoming SMS on a real phone, from the radio's PDUs to the alert: parsing and joining parts
 * ([SmsReceiver.incomingMessage]), the background check ([ProtectionPipeline]), the statistics
 * and the alert. Needs protection and SMS checking switched on, and alerts allowed.
 */
@RunWith(AndroidJUnit4::class)
class SmsProtectionDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ProtectionStore.get(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    /** Alerts already on the phone before the test; left alone. */
    private val earlierAlerts = alerts().map { it.id }.toSet()

    @After
    fun removeTestAlerts() {
        alerts().filter { it.id !in earlierAlerts }.forEach { notifications.cancel(it.id) }
    }

    @Test
    fun longSmsArrivesWhole() {
        val text = SCAM + " " + SystemClock.elapsedRealtime() // unique, so it's never taken for a repeat
        val message = SmsReceiver.incomingMessage(smsIntent(SENDER, text))
        assertNotNull(message)
        assertEquals(MessageSource.Sms, message!!.source)
        assertEquals(SENDER, message.sender)
        assertEquals(text, message.text)
    }

    @Test
    fun scamSmsIsCountedAndAlerted() {
        assertTrue("SMS protection is switched off", store.isActive(MessageSource.Sms))
        val text = SCAM + " " + SystemClock.elapsedRealtime()
        val before = store.state.value.stats
        check(text)
        val after = store.state.value.stats
        assertEquals(before.smsChecked + 1, after.smsChecked)
        assertEquals(before.threatsFound + 1, after.threatsFound)

        val alert = waitForAlert()
        assertNotNull("no alert was shown", alert)
        val shown = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .joinToString("\n") { alert!!.extras.getCharSequence(it)?.toString().orEmpty() }
        assertTrue(shown, context.getString(org.wizjer.android.R.string.alert_title) in shown)
        assertFalse("the alert repeats the message: $shown", "inpost-dostawa" in shown || "niedopłaty" in shown || SENDER in shown)
        // The app can show the message the alert is about.
        val flagged = AlertLog.flagged.value.first()
        assertEquals(text, flagged.message.text)
        assertTrue(alert!!.contentIntent != null)
    }

    @Test
    fun aParcelFeeWithNothingToActOnGetsAGentleNote() {
        val before = store.state.value.stats
        // Unique without digits: a long number would read as a phone number, something to act on.
        check("Pilne! Dopłać 2 zł do przesyłki, inaczej wróci do nadawcy. #" + SystemClock.elapsedRealtime().toString(36).filter(Char::isLetter))
        val note = waitForAlert()
        assertNotNull("no note was shown", note)
        assertEquals(CAREFUL_CHANNEL, note!!.channelId)
        assertEquals(context.getString(org.wizjer.android.R.string.alert_careful_title), note.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        // A note isn't counted among potential threats.
        assertEquals(before.threatsFound, store.state.value.stats.threatsFound)
    }

    @Test
    fun ordinarySmsIsCountedQuietly() {
        val before = store.state.value.stats
        check("Cześć, będę o 18. Kupić chleb? " + SystemClock.elapsedRealtime())
        val after = store.state.value.stats
        assertEquals(before.smsChecked + 1, after.smsChecked)
        assertEquals(before.threatsFound, after.threatsFound)
        assertEquals(null, waitForAlert(timeoutMillis = 500))
    }

    @Test
    fun theSmsAppsCopyIsNotCheckedAgain() {
        val text = SCAM + " " + SystemClock.elapsedRealtime()
        check(text)
        val before = store.state.value.stats
        val done = CountDownLatch(1)
        val copy = IncomingMessage(MessageSource.Notification, "+48 512 345 678", " $text\n", "com.google.android.apps.messaging", 0)
        ProtectionPipeline.onIncoming(context, copy) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(before.notificationsChecked, store.state.value.stats.notificationsChecked)
        assertEquals(before.threatsFound, store.state.value.stats.threatsFound)
    }

    @Test
    fun nothingIsCheckedWhileSmsCheckingIsOff() {
        val wasOn = store.state.value.smsEnabled
        store.setSmsEnabled(false)
        try {
            val before = store.state.value.stats
            check(SCAM + " " + SystemClock.elapsedRealtime())
            assertEquals(before.smsChecked, store.state.value.stats.smsChecked)
            assertEquals(before.threatsFound, store.state.value.stats.threatsFound)
            assertEquals(null, waitForAlert(timeoutMillis = 500))
        } finally {
            store.setSmsEnabled(wasOn)
        }
    }

    /** Runs [text] through the receiver's parsing and the pipeline, and waits for the check to finish. */
    private fun check(text: String) {
        val done = CountDownLatch(1)
        ProtectionPipeline.onIncoming(context, checkNotNull(SmsReceiver.incomingMessage(smsIntent(SENDER, text)))) { done.countDown() }
        assertTrue("the check didn't finish", done.await(20, TimeUnit.SECONDS))
    }

    /** Wizjer's warnings and gentle notes. */
    private fun alerts() = notifications.activeNotifications.filter { it.notification.channelId in setOf(ALERT_CHANNEL, CAREFUL_CHANNEL) }

    /** The alert this test raised, if one appears in time. */
    private fun waitForAlert(timeoutMillis: Long = 3_000): Notification? {
        val until = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < until) {
            alerts().firstOrNull { it.id !in earlierAlerts }?.let { return it.notification }
            SystemClock.sleep(100)
        }
        return null
    }

    /** An SMS_RECEIVED broadcast as the system sends it, split into parts like a real long SMS. */
    private fun smsIntent(from: String, text: String): Intent {
        val chunks = text.chunked(UCS2_CHARS_PER_PART)
        val pdus = chunks.mapIndexed { i, chunk -> deliverPdu(from, chunk, part = i + 1, parts = chunks.size) }
        return Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .putExtra("pdus", pdus.toTypedArray<Any>())
            .putExtra("format", "3gpp")
    }

    /** One GSM SMS-DELIVER PDU (3GPP TS 23.040) in UCS-2, with a concatenation header when split. */
    private fun deliverPdu(from: String, text: String, part: Int, parts: Int): ByteArray = ByteArrayOutputStream().apply {
        write(0x00) // no SMSC address
        write(if (parts > 1) 0x44 else 0x04) // SMS-DELIVER, no more messages, user data header if split
        val digits = from.removePrefix("+")
        write(digits.length)
        write(0x91) // international number
        (digits + "F".repeat(digits.length % 2)).chunked(2).forEach { write("${it[1]}${it[0]}".toInt(16)) }
        write(0x00) // protocol identifier
        write(0x08) // UCS-2
        listOf(0x62, 0x01, 0x32, 0x21, 0x00, 0x00, 0x80).forEach(::write) // 2026-10-23 12:00:00 +02:00
        val header = if (parts > 1) byteArrayOf(0x05, 0x00, 0x03, 0x2A, parts.toByte(), part.toByte()) else ByteArray(0)
        val body = text.toByteArray(Charsets.UTF_16BE)
        write(header.size + body.size)
        write(header)
        write(body)
    }.toByteArray()

    private companion object {
        const val ALERT_CHANNEL = "scam_alerts"
        const val CAREFUL_CHANNEL = "careful_notes"
        const val SENDER = "+48512345678"
        const val SCAM = "InPost: Twoja przesyłka została wstrzymana z powodu niedopłaty 1,99 zł. Dopłać w ciągu 24h: https://inpost-dostawa.top/pl"
        /** 134 bytes of text after the 6-byte concatenation header. */
        const val UCS2_CHARS_PER_PART = 67
    }
}
