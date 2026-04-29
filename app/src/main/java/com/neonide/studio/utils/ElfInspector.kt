package com.neonide.studio.utils

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ELF inspector.
 *
 * We use this to distinguish Android/Termux-compatible binaries (bionic linker or static)
 * from host GNU/Linux binaries (glibc interpreter like /lib64/ld-linux-*.so.*).
 */
object ElfInspector {

    private const val EI_NIDENT = 16
    private const val HEADER_SIZE = 64
    private const val MAGIC_0 = 0x7f
    private const val MAGIC_1 = 'E'.code
    private const val MAGIC_2 = 'L'.code
    private const val MAGIC_3 = 'F'.code
    private const val MAGIC_OFFSET_0 = 0
    private const val MAGIC_OFFSET_1 = 1
    private const val MAGIC_OFFSET_2 = 2
    private const val MAGIC_OFFSET_3 = 3

    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2

    private const val PT_INTERP = 3

    private const val EM_386 = 3
    private const val EM_ARM = 40
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183

    private const val HEADER_CLASS_OFFSET = 4
    private const val HEADER_DATA_OFFSET = 5
    private const val HEADER_DATA_LITTLE = 1

    private const val OFFSET_MACHINE = 18
    private const val OFFSET_PHOFF_64 = 32
    private const val OFFSET_PHENTSIZE_64 = 54
    private const val OFFSET_PHNUM_64 = 56
    private const val OFFSET_PHOFF_32 = 28
    private const val OFFSET_PHENTSIZE_32 = 42
    private const val OFFSET_PHNUM_32 = 44

    private const val PH_PTYPE_OFFSET = 0
    private const val PH_OFFSET_64 = 8
    private const val PH_FILESZ_64 = 32
    private const val PH_OFFSET_32 = 4
    private const val PH_FILESZ_32 = 16
    private const val MASK_8 = 0xff
    private const val MASK_16 = 0xffff
    private const val MASK_32 = 0xffffffffL
    private const val ZERO_I = 0
    private const val ZERO_L = 0L

    data class Info(
        val isElf: Boolean,
        val is64Bit: Boolean,
        val littleEndian: Boolean,
        val machine: Int,
        val interpreter: String?,
    )

    fun readInfo(file: File): Info? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(HEADER_SIZE)
                if (input.read(header) < EI_NIDENT) return@use null

                if (!isValidElf(header)) {
                    return@use Info(false, false, false, ZERO_I, null)
                }

                val clazz = header[HEADER_CLASS_OFFSET].toInt() and MASK_8
                val data = header[HEADER_DATA_OFFSET].toInt() and MASK_8
                val is64 = clazz == ELFCLASS64
                val little = data == HEADER_DATA_LITTLE
                val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

                val bb = ByteBuffer.wrap(header)
                bb.order(order)
                val machine = bb.getShort(OFFSET_MACHINE).toInt() and MASK_16

                val allBytes = file.readBytes()
                val b2 = ByteBuffer.wrap(allBytes)
                b2.order(order)

                Info(true, is64, little, machine, findInterpreter(b2, allBytes, is64))
            }
        }.getOrNull()
    }

    private fun isValidElf(header: ByteArray): Boolean {
        return header[MAGIC_OFFSET_0].toInt() == MAGIC_0 &&
            header[MAGIC_OFFSET_1].toInt() == MAGIC_1 &&
            header[MAGIC_OFFSET_2].toInt() == MAGIC_2 &&
            header[MAGIC_OFFSET_3].toInt() == MAGIC_3
    }

    private fun findInterpreter(b2: ByteBuffer, allBytes: ByteArray, is64: Boolean): String? {
        val (ePhoff, ePhentsize, ePhnum) = getElfHeaderInfo(b2, is64)
        if (ePhoff <= ZERO_L || ePhnum <= ZERO_I || ePhentsize <= ZERO_I) return null

        var interpreter: String? = null
        for (i in ZERO_I until ePhnum) {
            val off = (ePhoff + i.toLong() * ePhentsize.toLong()).toInt()
            if (off >= ZERO_I && off + ePhentsize <= allBytes.size && b2.getInt(off + PH_PTYPE_OFFSET) == PT_INTERP) {
                interpreter = getInterpreterString(b2, allBytes, off, is64)
                break
            }
        }
        return interpreter
    }

    private fun getElfHeaderInfo(b2: ByteBuffer, is64: Boolean): Triple<Long, Int, Int> {
        val ePhoff: Long
        val ePhentsize: Int
        val ePhnum: Int

        if (is64) {
            ePhoff = b2.getLong(OFFSET_PHOFF_64)
            ePhentsize = b2.getShort(OFFSET_PHENTSIZE_64).toInt() and MASK_16
            ePhnum = b2.getShort(OFFSET_PHNUM_64).toInt() and MASK_16
        } else {
            ePhoff = b2.getInt(OFFSET_PHOFF_32).toLong() and MASK_32
            ePhentsize = b2.getShort(OFFSET_PHENTSIZE_32).toInt() and MASK_16
            ePhnum = b2.getShort(OFFSET_PHNUM_32).toInt() and MASK_16
        }
        return Triple(ePhoff, ePhentsize, ePhnum)
    }

    private fun getInterpreterString(b2: ByteBuffer, allBytes: ByteArray, off: Int, is64: Boolean): String? {
        val pOffset: Long
        val pFilesz: Long
        if (is64) {
            pOffset = b2.getLong(off + PH_OFFSET_64)
            pFilesz = b2.getLong(off + PH_FILESZ_64)
        } else {
            pOffset = b2.getInt(off + PH_OFFSET_32).toLong() and MASK_32
            pFilesz = b2.getInt(off + PH_FILESZ_32).toLong() and MASK_32
        }

        val start = pOffset.toInt()
        val end = (pOffset + pFilesz).toInt().coerceAtMost(allBytes.size)
        return if (start in 0 until end) {
            val raw = allBytes.copyOfRange(start, end)
            raw.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
        } else null
    }

    /**
     * Return true if the ELF is likely runnable on Android/Termux on the current device.
     */
    fun isAndroidRunnable(file: File, supportedAbis: List<String>): Boolean {
        val info = readInfo(file)
        if (info == null || !info.isElf || !isMachineAllowed(info, supportedAbis)) {
            return false
        }

        val interp = info.interpreter
        return interp == null || interpIsRunnable(interp.lowercase())
    }

    private fun isMachineAllowed(info: Info, supportedAbis: List<String>): Boolean {
        val allowedMachines = supportedAbis.mapNotNull { abiToMachine(it) }.toSet()
        return allowedMachines.isEmpty() || info.machine in allowedMachines
    }

    private fun interpIsRunnable(lower: String): Boolean {
        val isGlibc = lower.contains("ld-linux")
        val isAndroid = lower.contains("linker")
        return !isGlibc && isAndroid
    }

    private fun abiToMachine(abi: String): Int? {
        return when (abi) {
            "arm64-v8a" -> EM_AARCH64
            "armeabi-v7a" -> EM_ARM
            "x86_64" -> EM_X86_64
            "x86" -> EM_386
            else -> null
        }
    }
}
