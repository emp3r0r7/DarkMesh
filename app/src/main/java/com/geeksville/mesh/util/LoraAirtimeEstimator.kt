package com.geeksville.mesh.util

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

object LoRaAirtimeEstimator {

    enum class RadioPreset(
        val displayName: String,
        val altName: String,
        val dataRateKbps: Double,
        val spreadingFactor: Int,
        val bandwidthHz: Double,
        /**
         * Coding rate index LoRa:
         *
         * 1 = 4/5
         * 2 = 4/6
         * 3 = 4/7
         * 4 = 4/8
         */
        val codingRate: Int,
        val linkBudgetDb: Double
    ) {
        SHORT_TURBO(
            displayName = "Short Range / Turbo",
            altName = "Short Turbo",
            dataRateKbps = 21.88,
            spreadingFactor = 7,
            bandwidthHz = 500_000.0,
            codingRate = 1,
            linkBudgetDb = 140.0
        ),

        SHORT_FAST(
            displayName = "Short Range / Fast",
            altName = "Short Fast",
            dataRateKbps = 10.94,
            spreadingFactor = 7,
            bandwidthHz = 250_000.0,
            codingRate = 1,
            linkBudgetDb = 143.0
        ),

        SHORT_SLOW(
            displayName = "Short Range / Slow",
            altName = "Short Slow",
            dataRateKbps = 6.25,
            spreadingFactor = 8,
            bandwidthHz = 250_000.0,
            codingRate = 1,
            linkBudgetDb = 145.5
        ),

        MEDIUM_FAST(
            displayName = "Medium Range / Fast",
            altName = "Medium Fast",
            dataRateKbps = 3.52,
            spreadingFactor = 9,
            bandwidthHz = 250_000.0,
            codingRate = 1,
            linkBudgetDb = 148.0
        ),

        MEDIUM_SLOW(
            displayName = "Medium Range / Slow",
            altName = "Medium Slow",
            dataRateKbps = 1.95,
            spreadingFactor = 10,
            bandwidthHz = 250_000.0,
            codingRate = 1,
            linkBudgetDb = 150.5
        ),

        LONG_TURBO(
            displayName = "Long Range / Turbo",
            altName = "Long Turbo",
            dataRateKbps = 1.34,
            spreadingFactor = 11,
            bandwidthHz = 500_000.0,
            codingRate = 4,
            linkBudgetDb = 150.0
        ),

        LONG_FAST(
            displayName = "Long Range / Fast",
            altName = "Long Fast",
            dataRateKbps = 1.07,
            spreadingFactor = 11,
            bandwidthHz = 250_000.0,
            codingRate = 1,
            linkBudgetDb = 153.0
        ),

        LONG_MODERATE(
            displayName = "Long Range / Moderate",
            altName = "Long Moderate",
            dataRateKbps = 0.34,
            spreadingFactor = 11,
            bandwidthHz = 125_000.0,
            codingRate = 4,
            linkBudgetDb = 156.0
        ),

        LONG_SLOW_DEPRECATED(
            displayName = "Long Range / Slow (deprecated)",
            altName = "Long Slow",
            dataRateKbps = 0.18,
            spreadingFactor = 12,
            bandwidthHz = 125_000.0,
            codingRate = 4,
            linkBudgetDb = 158.5
        )
    }

    data class AirtimeResult(
        val preset: RadioPreset,

        val originalBytes: Int,
        val compressedBytes: Int,
        val savedBytes: Int,

        val byteReductionPercent: Double,

        val originalAirtimeMs: Double,
        val compressedAirtimeMs: Double,
        val savedAirtimeMs: Double,
        val airtimeReductionPercent: Double,

        val originalAirtimeSeconds: Double,
        val compressedAirtimeSeconds: Double,
        val savedAirtimeSeconds: Double,

        val spreadingFactor: Int,
        val bandwidthHz: Double,
        val codingRate: Int,
        val preambleSymbols: Int,
        val crcEnabled: Boolean,
        val implicitHeader: Boolean,
        val lowDataRateOptimization: Boolean,
        val symbolTimeMs: Double
    ) {
        val hasSaving: Boolean
            get() = savedBytes > 0 && savedAirtimeMs > 0.0

        fun savedAirtimeLabel(): String {
            return "%.1f ms".format(savedAirtimeMs)
        }

        fun airtimeReductionLabel(): String {
            return "%.1f%%".format(airtimeReductionPercent)
        }

        fun byteReductionLabel(): String {
            return "%.1f%%".format(byteReductionPercent)
        }

        fun compactLabel(): String {
            return "Airtime RF stimato: -%.1f ms (-%.1f%%)".format(
                savedAirtimeMs,
                airtimeReductionPercent
            )
        }

        fun debugReport(): String {
            return buildString {
                appendLine("Preset: ${preset.altName}")
                appendLine("Original bytes: $originalBytes B")
                appendLine("Compressed bytes: $compressedBytes B")
                appendLine("Saved bytes: $savedBytes B")
                appendLine("Byte reduction: %.2f %%".format(byteReductionPercent))
                appendLine("Original airtime: %.2f ms".format(originalAirtimeMs))
                appendLine("Compressed airtime: %.2f ms".format(compressedAirtimeMs))
                appendLine("Saved airtime: %.2f ms".format(savedAirtimeMs))
                appendLine("Airtime reduction: %.2f %%".format(airtimeReductionPercent))
                appendLine("SF: $spreadingFactor")
                appendLine("BW: %.0f Hz".format(bandwidthHz))
                appendLine("CR: 4/${codingRate + 4}")
                appendLine("Preamble: $preambleSymbols symbols")
                appendLine("CRC: $crcEnabled")
                appendLine("Implicit header: $implicitHeader")
                appendLine("Low data rate optimization: $lowDataRateOptimization")
                appendLine("Tsym: %.3f ms".format(symbolTimeMs))
            }
        }
    }

    fun estimateSaving(
        originalBytes: Int,
        compressedBytes: Int,
        preset: RadioPreset = RadioPreset.MEDIUM_FAST,
        preambleSymbols: Int = 8,
        crcEnabled: Boolean = true,
        implicitHeader: Boolean = false,
        lowDataRateOptimization: Boolean = shouldEnableLowDataRateOptimization(
            spreadingFactor = preset.spreadingFactor,
            bandwidthHz = preset.bandwidthHz
        )
    ): AirtimeResult {
        require(originalBytes >= 0) {
            "originalBytes must be >= 0"
        }

        require(compressedBytes >= 0) {
            "compressedBytes must be >= 0"
        }

        require(preambleSymbols >= 0) {
            "preambleSymbols must be >= 0"
        }

        val originalAirtimeMs = calculateAirtimeMs(
            payloadBytes = originalBytes,
            preset = preset,
            preambleSymbols = preambleSymbols,
            crcEnabled = crcEnabled,
            implicitHeader = implicitHeader,
            lowDataRateOptimization = lowDataRateOptimization
        )

        val compressedAirtimeMs = calculateAirtimeMs(
            payloadBytes = compressedBytes,
            preset = preset,
            preambleSymbols = preambleSymbols,
            crcEnabled = crcEnabled,
            implicitHeader = implicitHeader,
            lowDataRateOptimization = lowDataRateOptimization
        )

        val savedBytes = originalBytes - compressedBytes

        val byteReductionPercent =
            if (originalBytes > 0) {
                savedBytes.toDouble() / originalBytes.toDouble() * 100.0
            } else {
                0.0
            }

        val savedAirtimeMs =
            originalAirtimeMs - compressedAirtimeMs

        val airtimeReductionPercent =
            if (originalAirtimeMs > 0.0) {
                savedAirtimeMs / originalAirtimeMs * 100.0
            } else {
                0.0
            }

        val symbolTimeMs = calculateSymbolTimeMs(
            spreadingFactor = preset.spreadingFactor,
            bandwidthHz = preset.bandwidthHz
        )

        return AirtimeResult(
            preset = preset,

            originalBytes = originalBytes,
            compressedBytes = compressedBytes,
            savedBytes = savedBytes,

            byteReductionPercent = byteReductionPercent,

            originalAirtimeMs = originalAirtimeMs,
            compressedAirtimeMs = compressedAirtimeMs,
            savedAirtimeMs = savedAirtimeMs,
            airtimeReductionPercent = airtimeReductionPercent,

            originalAirtimeSeconds = originalAirtimeMs / 1000.0,
            compressedAirtimeSeconds = compressedAirtimeMs / 1000.0,
            savedAirtimeSeconds = savedAirtimeMs / 1000.0,

            spreadingFactor = preset.spreadingFactor,
            bandwidthHz = preset.bandwidthHz,
            codingRate = preset.codingRate,
            preambleSymbols = preambleSymbols,
            crcEnabled = crcEnabled,
            implicitHeader = implicitHeader,
            lowDataRateOptimization = lowDataRateOptimization,
            symbolTimeMs = symbolTimeMs
        )
    }

    fun calculateAirtimeMs(
        payloadBytes: Int,
        preset: RadioPreset = RadioPreset.MEDIUM_FAST,
        preambleSymbols: Int = 8,
        crcEnabled: Boolean = true,
        implicitHeader: Boolean = false,
        lowDataRateOptimization: Boolean = shouldEnableLowDataRateOptimization(
            spreadingFactor = preset.spreadingFactor,
            bandwidthHz = preset.bandwidthHz
        )
    ): Double {
        require(payloadBytes >= 0) {
            "payloadBytes must be >= 0"
        }

        require(preset.spreadingFactor in 6..12) {
            "spreadingFactor must be in 6..12"
        }

        require(preset.bandwidthHz > 0.0) {
            "bandwidthHz must be > 0"
        }

        require(preset.codingRate in 1..4) {
            "codingRate must be 1..4"
        }

        require(preambleSymbols >= 0) {
            "preambleSymbols must be >= 0"
        }

        val sf = preset.spreadingFactor
        val bw = preset.bandwidthHz
        val cr = preset.codingRate

        val crc = if (crcEnabled) 1 else 0
        val ih = if (implicitHeader) 1 else 0
        val de = if (lowDataRateOptimization) 1 else 0

        val symbolTimeSeconds =
            2.0.pow(sf.toDouble()) / bw

        val preambleTimeSeconds =
            (preambleSymbols + 4.25) * symbolTimeSeconds

        val numerator =
            8.0 * payloadBytes -
                    4.0 * sf +
                    28.0 +
                    16.0 * crc -
                    20.0 * ih

        val denominator =
            4.0 * (sf - 2 * de)

        val payloadSymbolBlocks =
            max(
                ceil(numerator / denominator).toInt(),
                0
            )

        val payloadSymbols =
            8 + payloadSymbolBlocks * (cr + 4)

        val payloadTimeSeconds =
            payloadSymbols * symbolTimeSeconds

        val totalTimeSeconds =
            preambleTimeSeconds + payloadTimeSeconds

        return totalTimeSeconds * 1000.0
    }

    fun calculateSymbolTimeMs(
        spreadingFactor: Int,
        bandwidthHz: Double
    ): Double {
        require(spreadingFactor in 6..12) {
            "spreadingFactor must be in 6..12"
        }

        require(bandwidthHz > 0.0) {
            "bandwidthHz must be > 0"
        }

        return 2.0.pow(spreadingFactor.toDouble()) / bandwidthHz * 1000.0
    }

    fun shouldEnableLowDataRateOptimization(
        spreadingFactor: Int,
        bandwidthHz: Double
    ): Boolean {
        val symbolTimeMs = calculateSymbolTimeMs(
            spreadingFactor = spreadingFactor,
            bandwidthHz = bandwidthHz
        )

        return symbolTimeMs >= 16.0
    }

    fun estimateAirtimeSaving(
        originalBytes: Int,
        compressedBytes: Int,
        spreadingFactor: Int,
        ): AirtimeResult {

        val preset = when (spreadingFactor) {
            7 -> RadioPreset.SHORT_FAST
            8 -> RadioPreset.SHORT_SLOW
            9 -> RadioPreset.MEDIUM_FAST
            10 -> RadioPreset.MEDIUM_SLOW
            11 -> RadioPreset.LONG_FAST
            12 -> RadioPreset.LONG_SLOW_DEPRECATED
            else -> RadioPreset.MEDIUM_FAST
        }

        return estimateSaving(
            originalBytes = originalBytes,
            compressedBytes = compressedBytes,
            preset
        )
    }
}