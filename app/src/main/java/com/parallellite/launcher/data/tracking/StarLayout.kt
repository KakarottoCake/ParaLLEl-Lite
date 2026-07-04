package com.parallellite.launcher.data.tracking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Decodes the collected-star count from a RetroArch `.srm` save, ported from
 * parallel-launcher's `StarLayout` (src/rhdc/core/layout.cpp). A layout is either
 * downloaded per-hack (RHDC "advanced" layout JSON) or the vanilla SM64 default.
 *
 * The `.srm` groups all save backings into fixed regions; we read the region for
 * the save type, then for each slot sum the population count of `byte & mask`
 * over every star position, taking the best slot.
 */
class StarLayout(
    private val numSlots: Int,
    private val slotsStart: Int,
    private val slotSize: Int,
    private val activeBit: Int,
    private val saveFormat: SaveFormat,
    private val stars: List<StarData>,
) {
    data class StarData(val offset: Int, val mask: Int)

    enum class SaveFormat(val regionOffset: Int) {
        EEPROM(0x0),
        SRAM(0x20800),
        FlashRAM(0x28800),
        MemPak(0x800),
        RawSRM(0x0),
    }

    /** Total number of star bits defined by the layout (upper bound of collectable). */
    val definedStarBits: Int = stars.sumOf { Integer.bitCount(it.mask and 0xFF) }

    /** Highest collected-star count across all save slots. */
    fun countStars(srm: ByteArray): Int {
        var best = 0
        for (slot in 0 until numSlots) {
            best = maxOf(best, countSlot(srm, slot))
        }
        return best
    }

    private fun countSlot(srm: ByteArray, slot: Int): Int {
        val base = saveFormat.regionOffset
        // Slot must be marked active, or it holds no progress.
        val activeIdx = base + activeBit / 8
        if (activeIdx !in srm.indices) return 0
        if ((srm[activeIdx].toInt() and (0x80 ushr (activeBit % 8))) == 0) return 0

        var count = 0
        for (star in stars) {
            val idx = base + slotsStart + slot * slotSize + star.offset
            if (idx in srm.indices) {
                count += Integer.bitCount(srm[idx].toInt() and star.mask and 0xFF)
            }
        }
        return count
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** Parses an RHDC advanced-layout JSON document. */
        fun parse(jsonText: String): StarLayout {
            val dto = json.decodeFromString(LayoutDto.serializer(), jsonText)
            val fmt = when (dto.format.saveType.uppercase()) {
                "SRAM" -> SaveFormat.SRAM
                "FLASHRAM" -> SaveFormat.FlashRAM
                "MEMPAK" -> SaveFormat.MemPak
                "RAWSRM", "MULTI" -> SaveFormat.RawSRM
                else -> SaveFormat.EEPROM
            }
            val stars = dto.groups
                .flatMap { it.courses }
                .flatMap { it.data }
                .map { StarData(it.offset, it.mask) }
            return StarLayout(
                numSlots = dto.format.numSlots.coerceAtLeast(1),
                slotsStart = dto.format.slotsStart,
                slotSize = dto.format.slotSize.coerceAtLeast(1),
                activeBit = dto.format.activeBit,
                saveFormat = fmt,
                stars = stars,
            )
        }

        /** Vanilla SM64 EEPROM layout (parallel-launcher `StarLayout::createDefault`). */
        fun vanillaSm64(): StarLayout {
            val main = (12..26).map { StarData(it, 0x7F) }        // courses 1–15
            val secretOffsets = listOf(8, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36)
            val secret = secretOffsets.map { StarData(it, 0x7F) }
            return StarLayout(
                numSlots = 4,
                slotsStart = 0,
                slotSize = 112,
                activeBit = 95,
                saveFormat = SaveFormat.EEPROM,
                stars = main + secret,
            )
        }
    }
}

@Serializable
private data class LayoutFormatDto(
    @SerialName("save_type") val saveType: String = "EEPROM",
    @SerialName("num_slots") val numSlots: Int = 4,
    @SerialName("slots_start") val slotsStart: Int = 0,
    @SerialName("slot_size") val slotSize: Int = 112,
    @SerialName("active_bit") val activeBit: Int = 95,
)

@Serializable
private data class LayoutCourseDto(val data: List<LayoutStarDto> = emptyList())

@Serializable
private data class LayoutStarDto(val offset: Int = 0, val mask: Int = 0)

@Serializable
private data class LayoutGroupDto(val courses: List<LayoutCourseDto> = emptyList())

@Serializable
private data class LayoutDto(
    val format: LayoutFormatDto = LayoutFormatDto(),
    val groups: List<LayoutGroupDto> = emptyList(),
)
