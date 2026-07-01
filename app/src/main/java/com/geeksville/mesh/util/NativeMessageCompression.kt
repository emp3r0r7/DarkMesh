package com.geeksville.mesh.util

object NativeMessageCompression {

    private const val MAX_TEXT_BYTES = 256

    @Volatile
    private var nativeLoadError: Throwable? = null

    init {
        runCatching {
            System.loadLibrary("unishox2")
            System.loadLibrary("darkmesh_compression")
        }.onFailure {
            nativeLoadError = it
        }
    }

    val isBridgeLoaded: Boolean
        get() = nativeLoadError == null

    val loadErrorMessage: String?
        get() = nativeLoadError?.message

    val isUnishoxAvailable: Boolean
        get() = isBridgeLoaded && runCatching { isUnishoxAvailableNative() }.getOrDefault(false)

    fun compress(input: ByteArray): ByteArray? {
        if (!canUse(input)) return null
        return runCatching { compressNative(input) }.getOrNull()
    }

    fun decompress(input: ByteArray): ByteArray? {
        if (!canUse(input)) return null
        return runCatching { decompressNative(input) }.getOrNull()
    }

    fun compressText(text: String): ByteArray? = compress(text.encodeToByteArray())

    fun decompressText(input: ByteArray): String? =
        decompress(input)?.let { bytes ->
            runCatching { bytes.decodeToString() }.getOrNull()
        }

    private fun canUse(input: ByteArray): Boolean =
        isBridgeLoaded && input.isNotEmpty() && input.size <= MAX_TEXT_BYTES

    private external fun isUnishoxAvailableNative(): Boolean

    private external fun compressNative(input: ByteArray): ByteArray?

    private external fun decompressNative(input: ByteArray): ByteArray?
}
