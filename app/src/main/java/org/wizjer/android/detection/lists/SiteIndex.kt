package org.wizjer.android.detection.lists

import androidx.annotation.StringRes
import org.wizjer.android.R
import org.wizjer.android.detection.EntityExtractor
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.IDN
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale

/** The downloaded lists of flagged sites. [id] is stored in the index file, so never reuse one. */
enum class SiteList(val id: Int, @StringRes val label: Int) {
    /** CERT Polska's warning list: phishing, fake shops, fake investments. */
    Cert(1, R.string.list_cert),
    Shortener(2, R.string.list_shortener),
    Gambling(3, R.string.list_gambling),
    Adult(4, R.string.list_adult),
    Piracy(5, R.string.list_piracy),
}

/**
 * Flagged domains, stored compactly: each domain is kept as a 64-bit hash, sorted per list, in a
 * memory-mapped file. About a million domains take ~7 MB on disk and almost no memory, and a
 * lookup is a binary search taking microseconds. Everything is checked on the phone.
 *
 * File layout (big-endian): magic, format version, download time (epoch ms), number of lists,
 * then each list's id and size, padding to 8 bytes, then each list's sorted hashes.
 */
class SiteIndex private constructor(
    private val buffer: ByteBuffer,
    private val sections: Map<SiteList, Section>,
    /** When the lists were downloaded (epoch ms). */
    val updatedAt: Long,
) {
    private class Section(val offset: Int, val size: Int)

    /** Number of domains on all lists. */
    val size: Int get() = sections.values.sumOf { it.size }

    fun size(list: SiteList): Int = sections[list]?.size ?: 0

    /** The lists [host] is on, counting its parent domains too ("a.shop.example.com" → "example.com"). */
    fun listsFor(host: String): Set<SiteList> {
        val domain = normalize(host) ?: return emptySet()
        val digest = sha256()
        val hashes = candidates(domain).map { hash(it, digest) }
        return sections.filterValues { section -> hashes.any { contains(section, it) } }.keys
    }

    private fun contains(section: Section, value: Long): Boolean {
        var low = 0
        var high = section.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val found = buffer.getLong(section.offset + mid * 8)
            when {
                found < value -> low = mid + 1
                found > value -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** Collects domains list by list, then writes them as an index file. */
    class Builder {
        private val digest = sha256()
        private val hashes = SiteList.entries.associateWith { LongList() }

        val isEmpty: Boolean get() = hashes.values.all { it.size == 0 }

        fun add(list: SiteList, domain: String) {
            normalize(domain)?.let { hashes.getValue(list).add(hash(it, digest)) }
        }

        fun writeTo(file: File, updatedAt: Long) {
            val sorted = hashes.mapValues { it.value.sortedDistinct() }
            val header = 20 + 8 * sorted.size
            DataOutputStream(BufferedOutputStream(file.outputStream(), 1 shl 16)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(updatedAt)
                out.writeInt(sorted.size)
                sorted.forEach { (list, values) ->
                    out.writeInt(list.id)
                    out.writeInt(values.size)
                }
                repeat(padding(header)) { out.writeByte(0) }
                sorted.values.forEach { values -> values.forEach(out::writeLong) }
            }
        }
    }

    /** A growable array of longs, without boxing a million values. */
    private class LongList {
        private var values = LongArray(1024)
        var size = 0
            private set

        fun add(value: Long) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun sortedDistinct(): LongArray {
            val sorted = values.copyOf(size).apply { sort() }
            var unique = 0
            for (value in sorted) if (unique == 0 || value != sorted[unique - 1]) sorted[unique++] = value
            return sorted.copyOf(unique)
        }
    }

    companion object {
        private const val MAGIC = 0x575A5349 // "WZSI"
        private const val VERSION = 1

        /** Opens an index file; null if it's missing or damaged. */
        fun open(file: File): SiteIndex? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                // The mapping stays valid after the file is closed.
                val buffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                check(buffer.getInt(0) == MAGIC && buffer.getInt(4) == VERSION)
                val updatedAt = buffer.getLong(8)
                val count = buffer.getInt(16)
                val header = 20 + 8 * count
                var offset = header + padding(header)
                val sections = buildMap {
                    repeat(count) { i ->
                        val id = buffer.getInt(20 + 8 * i)
                        val size = buffer.getInt(24 + 8 * i)
                        // Lists this version doesn't know are skipped.
                        SiteList.entries.firstOrNull { it.id == id }?.let { put(it, Section(offset, size)) }
                        offset += size * 8
                    }
                }
                check(offset.toLong() == raf.length())
                SiteIndex(buffer, sections, updatedAt)
            }
        }.getOrNull()

        /** "WWW.Example.com." → "example.com"; non-Latin names → punycode. Null if it isn't a domain. */
        fun normalize(raw: String): String? {
            var domain = raw.trim().lowercase(Locale.ROOT).trimEnd('.').removePrefix("*.").removePrefix("www.")
            if (domain.any { it.code > 127 }) {
                domain = runCatching { IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED) }.getOrNull() ?: return null
            }
            return domain.takeIf { '.' in it && it.none { c -> c.isWhitespace() || c == '/' } }
        }

        /** The domain itself and its parents, down to the registrable domain ("shop.example.com" → "example.com"). */
        private fun candidates(domain: String): List<String> {
            if (domain.all { it.isDigit() || it == '.' }) return listOf(domain) // IP address
            val registrable = EntityExtractor.registrableDomain(domain)
            return generateSequence(domain) { current ->
                current.substringAfter('.', "").takeIf { it.length >= registrable.length }
            }.toList()
        }

        private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

        /** First 8 bytes of the domain's SHA-256. */
        private fun hash(domain: String, digest: MessageDigest): Long {
            val bytes = digest.digest(domain.toByteArray(Charsets.UTF_8))
            var value = 0L
            for (i in 0 until 8) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
            return value
        }

        private fun padding(length: Int) = (8 - length % 8) % 8
    }
}
