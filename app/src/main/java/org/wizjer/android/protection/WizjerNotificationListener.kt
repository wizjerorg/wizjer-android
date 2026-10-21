package org.wizjer.android.protection

import android.service.notification.NotificationListenerService

/**
 * Bound by the system once the user grants notification access.
 * Scam analysis of posted notifications will be added here.
 */
class WizjerNotificationListener : NotificationListenerService()
