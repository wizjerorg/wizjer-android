package org.wizjer.android.detection.model

/**
 * BLAKE2b (RFC 7693), unkeyed, for digests of 1–64 bytes. The model hashes its features with an
 * 8-byte BLAKE2b, as Python's `hashlib.blake2b(digest_size=8)` does in the training code.
 * Android has no built-in BLAKE2.
 */
internal object Blake2b {

    private val IV = longArrayOf(
        0x6a09e667f3bcc908, 0xbb67ae8584caa73buL.toLong(), 0x3c6ef372fe94f82b, 0xa54ff53a5f1d36f1uL.toLong(),
        0x510e527fade682d1, 0x9b05688c2b3e6c1fuL.toLong(), 0x1f83d9abfb41bd6b, 0x5be0cd19137e2179,
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
    )

    /** The digest of [input], [length] bytes long. */
    fun digest(input: ByteArray, length: Int): ByteArray {
        val h = state(input, length)
        return ByteArray(length) { i -> (h[i / 8] ushr (8 * (i % 8))).toByte() }
    }

    /** The first 8 digest bytes as a little-endian number: what Python's int.from_bytes(…, "little") gives. */
    fun first64(input: ByteArray, length: Int = 8): Long = state(input, length)[0]

    private fun state(input: ByteArray, length: Int): LongArray {
        require(length in 1..64) { "BLAKE2b digests are 1 to 64 bytes" }
        val h = IV.copyOf()
        h[0] = h[0] xor (0x01010000L or length.toLong())
        val m = LongArray(16)
        val v = LongArray(16)
        var offset = 0
        while (input.size - offset > BLOCK) {
            load(input, offset, BLOCK, m)
            offset += BLOCK
            compress(h, m, v, offset.toLong(), last = false)
        }
        load(input, offset, input.size - offset, m)
        compress(h, m, v, input.size.toLong(), last = true)
        return h
    }

    /** Reads [count] bytes as little-endian words, zero-padding the rest of the block. */
    private fun load(input: ByteArray, offset: Int, count: Int, m: LongArray) {
        m.fill(0)
        for (i in 0 until count) {
            m[i / 8] = m[i / 8] or ((input[offset + i].toLong() and 0xFF) shl (8 * (i % 8)))
        }
    }

    private fun compress(h: LongArray, m: LongArray, v: LongArray, counter: Long, last: Boolean) {
        h.copyInto(v, 0)
        IV.copyInto(v, 8)
        v[12] = v[12] xor counter
        if (last) v[14] = v[14].inv()
        for (round in 0 until 12) {
            val s = SIGMA[round % 10]
            mix(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            mix(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            mix(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            mix(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            mix(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            mix(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            mix(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            mix(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[i + 8]
    }

    private fun mix(v: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] += v[b] + x
        v[d] = (v[d] xor v[a]).rotateRight(32)
        v[c] += v[d]
        v[b] = (v[b] xor v[c]).rotateRight(24)
        v[a] += v[b] + y
        v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] += v[d]
        v[b] = (v[b] xor v[c]).rotateRight(63)
    }

    private const val BLOCK = 128
}
