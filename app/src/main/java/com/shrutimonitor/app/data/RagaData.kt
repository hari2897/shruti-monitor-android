package com.shrutimonitor.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents the 12 swarasthanas (semitone positions) of Indian classical music.
 *
 * Each swara has both Hindustani and Carnatic nomenclature along with its
 * Just Intonation (JI) frequency ratio relative to Sa.
 *
 * Index 0 = Sa (tonic), through Index 11 = Shuddh Ni / Kakali Nishadham.
 */
enum class Swara(
    val index: Int,
    val hindustaniName: String,
    val hindustaniAbbr: String,
    val carnaticName: String,
    val carnaticAbbr: String,
    val jiRatio: Double
) {
    SA(0, "Sa", "S", "Shadjam", "S", 1.0),
    KOMAL_RE(1, "Komal Re", "r", "Shuddha Rishabham", "R1", 16.0 / 15.0),
    SHUDDH_RE(2, "Shuddh Re", "R", "Chatusruti Rishabham", "R2", 9.0 / 8.0),
    KOMAL_GA(3, "Komal Ga", "g", "Sadharana Gandharam", "G1", 6.0 / 5.0),
    SHUDDH_GA(4, "Shuddh Ga", "G", "Antara Gandharam", "G2", 5.0 / 4.0),
    SHUDDH_MA(5, "Shuddh Ma", "m", "Shuddha Madhyamam", "M1", 4.0 / 3.0),
    TIVRA_MA(6, "Tivra Ma", "M", "Prati Madhyamam", "M2", 45.0 / 32.0),
    PA(7, "Pa", "P", "Panchamam", "P", 3.0 / 2.0),
    KOMAL_DHA(8, "Komal Dha", "d", "Shuddha Dhaivatham", "D1", 8.0 / 5.0),
    SHUDDH_DHA(9, "Shuddh Dha", "D", "Chatusruti Dhaivatham", "D2", 5.0 / 3.0),
    KOMAL_NI(10, "Komal Ni", "n", "Kaisiki Nishadham", "N1", 9.0 / 5.0),
    SHUDDH_NI(11, "Shuddh Ni", "N", "Kakali Nishadham", "N2", 15.0 / 8.0);

    companion object {
        /**
         * Returns the Swara corresponding to the given index (0-11).
         * @throws IllegalArgumentException if index is out of range.
         */
        fun fromIndex(index: Int): Swara {
            return entries.firstOrNull { it.index == index }
                ?: throw IllegalArgumentException("Invalid swara index: $index. Must be 0-11.")
        }

        /**
         * Returns the frequency in Hz for a given swara, based on the Sa frequency.
         * Supports octave offset (0 = same octave, 1 = upper, -1 = lower).
         */
        fun frequencyHz(swara: Swara, saFrequencyHz: Double, octaveOffset: Int = 0): Double {
            val octaveMultiplier = Math.pow(2.0, octaveOffset.toDouble())
            return saFrequencyHz * swara.jiRatio * octaveMultiplier
        }

        /**
         * Returns the Equal Temperament (12-TET) cent value for a swara index.
         * Each semitone = 100 cents.
         */
        fun equalTemperamentCents(swaraIndex: Int): Double {
            return swaraIndex * 100.0
        }

        /**
         * Returns the Just Intonation cent value for a swara.
         * cents = 1200 * log2(ratio)
         */
        fun justIntonationCents(swara: Swara): Double {
            return 1200.0 * (Math.log(swara.jiRatio) / Math.log(2.0))
        }

        private val JI_CENTS = doubleArrayOf(
            0.0,
            111.731,
            203.910,
            315.641,
            386.314,
            498.045,
            590.224,
            701.955,
            813.686,
            884.359,
            1017.596,
            1088.269,
            1200.0
        )

        /**
         * Maps actual cents relative to Sa to visual cents where each semitone is exactly 100 cents.
         */
        fun actualToVisualCents(actualCents: Double): Double {
            val octave = kotlin.math.floor(actualCents / 1200.0).toInt()
            val remainder = actualCents - octave * 1200.0
            
            var k = 0
            while (k < 12 && remainder >= JI_CENTS[k + 1]) {
                k++
            }
            
            val lowJI = JI_CENTS[k]
            val highJI = JI_CENTS[k + 1]
            val lowVisual = k * 100.0
            val highVisual = (k + 1) * 100.0
            
            val fraction = (remainder - lowJI) / (highJI - lowJI)
            val visualRemainder = lowVisual + fraction * (highVisual - lowVisual)
            
            return octave * 1200.0 + visualRemainder
        }
    }
}

/**
 * Represents a raga with its melodic structure and metadata.
 *
 * @property name Primary name of the raga.
 * @property altNames Alternative/variant spellings or names.
 * @property thaat Parent thaat (Hindustani) — null for Carnatic ragas.
 * @property melaKartaNumber Parent melakarta number (Carnatic) — null for Hindustani ragas.
 * @property aaroha Ascending scale (list of Swaras, always starts with SA).
 * @property avaroha Descending scale (list of Swaras, always starts with the highest note).
 * @property activeSwaras The set of all swaras used in the raga.
 * @property vadi The dominant/most important note (Hindustani concept).
 * @property samvadi The sub-dominant note, typically a fourth or fifth from vadi.
 * @property isHindustani True if this is a Hindustani raga, false if Carnatic.
 */
data class Raga(
    val name: String,
    val altNames: List<String> = emptyList(),
    val thaat: String? = null,
    val melaKartaNumber: Int? = null,
    val aaroha: List<Swara>,
    val avaroha: List<Swara>,
    val activeSwaras: Set<Swara>,
    val vadi: Swara? = null,
    val samvadi: Swara? = null,
    val isHindustani: Boolean = true
) {
    /** Number of swaras in the raga (pentatonic=5, hexatonic=6, heptatonic=7, etc.) */
    val swaraCount: Int get() = activeSwaras.size

    /** True if this is a sampurna (complete 7-note) raga */
    val isSampurna: Boolean get() = swaraCount == 7

    /** True if this is an audava (pentatonic) raga */
    val isAudava: Boolean get() = swaraCount == 5

    /** True if this is a shadava (hexatonic) raga */
    val isShadava: Boolean get() = swaraCount == 6

    /**
     * Returns the cent values for each active swara using Just Intonation.
     */
    fun activeJiCents(): List<Pair<Swara, Double>> {
        return activeSwaras.sortedBy { it.index }.map { swara ->
            swara to Swara.justIntonationCents(swara)
        }
    }
}

/**
 * Represents a Carnatic melakarta (parent scale).
 *
 * @property number Melakarta number (1-72).
 * @property name Name of the melakarta.
 * @property chakra Chakra grouping name (Indu, Netra, Agni, etc.).
 * @property swaras The 7 swaras of the melakarta scale.
 * @property janyaRagas Derived (janya) ragas from this melakarta.
 */
data class MelaKarta(
    val number: Int,
    val name: String,
    val chakra: String,
    val swaras: List<Swara>,
    val janyaRagas: List<Raga> = emptyList()
) {
    /** Aaroha — ascending order of swaras */
    val aaroha: List<Swara> get() = swaras.sortedBy { it.index }

    /** Avaroha — descending order of swaras */
    val avaroha: List<Swara> get() = swaras.sortedByDescending { it.index }

    /** Whether this melakarta uses Prati Madhyamam (Ma2) — "Prati Madhyama" melakartas are 37-72 */
    val isPratiMadhyama: Boolean get() = number > 36

    /** Converts this melakarta into a Raga object */
    fun toRaga(): Raga = Raga(
        name = name,
        melaKartaNumber = number,
        aaroha = aaroha,
        avaroha = avaroha,
        activeSwaras = swaras.toSet(),
        isHindustani = false
    )
}

/**
 * Repository for loading and querying raga data from bundled JSON assets.
 *
 * JSON files are loaded lazily on first access.
 */
class RagaRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // Lazy-loaded caches
    private val hindustaniRagasCache: List<Raga> by lazy { loadHindustaniRagas() }
    private val carnaticMelakartasCache: List<MelaKarta> by lazy { loadCarnaticMelakartas() }

    /**
     * Returns all loaded Hindustani ragas.
     */
    fun getHindustaniRagas(): List<Raga> = hindustaniRagasCache

    /**
     * Returns all 72 Carnatic melakarta ragas.
     */
    fun getCarnaticMelakartas(): List<MelaKarta> = carnaticMelakartasCache

    /**
     * Returns all Carnatic ragas (melakartas + janya ragas) as Raga objects.
     */
    fun getCarnaticRagas(): List<Raga> {
        val ragas = mutableListOf<Raga>()
        for (melakarta in carnaticMelakartasCache) {
            ragas.add(melakarta.toRaga())
            ragas.addAll(melakarta.janyaRagas)
        }
        return ragas
    }

    /**
     * Searches all ragas (both Hindustani and Carnatic) by name substring (case-insensitive).
     */
    fun searchRagas(query: String): List<Raga> {
        val lowerQuery = query.lowercase()
        val results = mutableListOf<Raga>()

        // Search Hindustani ragas
        results.addAll(
            hindustaniRagasCache.filter { raga ->
                raga.name.lowercase().contains(lowerQuery) ||
                    raga.altNames.any { it.lowercase().contains(lowerQuery) } ||
                    raga.thaat?.lowercase()?.contains(lowerQuery) == true
            }
        )

        // Search Carnatic ragas (melakartas + janyas)
        for (melakarta in carnaticMelakartasCache) {
            if (melakarta.name.lowercase().contains(lowerQuery)) {
                results.add(melakarta.toRaga())
            }
            results.addAll(
                melakarta.janyaRagas.filter { raga ->
                    raga.name.lowercase().contains(lowerQuery) ||
                        raga.altNames.any { it.lowercase().contains(lowerQuery) }
                }
            )
        }

        return results
    }

    /**
     * Finds a raga by exact name (case-insensitive), checking primary name and alt names.
     * Returns null if not found.
     */
    fun getRagaByName(name: String): Raga? {
        val lowerName = name.lowercase()

        // Check Hindustani ragas
        hindustaniRagasCache.forEach { raga ->
            if (raga.name.lowercase() == lowerName ||
                raga.altNames.any { it.lowercase() == lowerName }
            ) {
                return raga
            }
        }

        // Check Carnatic melakartas and janyas
        for (melakarta in carnaticMelakartasCache) {
            if (melakarta.name.lowercase() == lowerName) {
                return melakarta.toRaga()
            }
            melakarta.janyaRagas.forEach { raga ->
                if (raga.name.lowercase() == lowerName ||
                    raga.altNames.any { it.lowercase() == lowerName }
                ) {
                    return raga
                }
            }
        }

        return null
    }

    /**
     * Returns a melakarta by its number (1-72).
     */
    fun getMelakartaByNumber(number: Int): MelaKarta? {
        return carnaticMelakartasCache.firstOrNull { it.number == number }
    }

    /**
     * Finds ragas that use exactly the given set of swaras.
     */
    fun findRagasBySwaras(swaras: Set<Swara>): List<Raga> {
        val results = mutableListOf<Raga>()

        results.addAll(hindustaniRagasCache.filter { it.activeSwaras == swaras })

        for (melakarta in carnaticMelakartasCache) {
            if (melakarta.swaras.toSet() == swaras) {
                results.add(melakarta.toRaga())
            }
            results.addAll(melakarta.janyaRagas.filter { it.activeSwaras == swaras })
        }

        return results
    }

    // ── Private JSON loading ──────────────────────────────────────────

    private fun loadHindustaniRagas(): List<Raga> {
        val jsonString = context.assets.open("hindustani_ragas.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = json.parseToJsonElement(jsonString).jsonArray
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val thaat = obj["thaat"]?.jsonPrimitive?.content
            val aarohaIndices = obj["aaroha"]!!.jsonArray.map { it.jsonPrimitive.int }
            val avarohaIndices = obj["avaroha"]!!.jsonArray.map { it.jsonPrimitive.int }
            val swaraIndices = obj["swaras"]!!.jsonArray.map { it.jsonPrimitive.int }
            val vadiIndex = obj["vadi"]?.jsonPrimitive?.intOrNull
            val samvadiIndex = obj["samvadi"]?.jsonPrimitive?.intOrNull

            Raga(
                name = name,
                thaat = thaat,
                aaroha = aarohaIndices.map { Swara.fromIndex(it) },
                avaroha = avarohaIndices.map { Swara.fromIndex(it) },
                activeSwaras = swaraIndices.map { Swara.fromIndex(it) }.toSet(),
                vadi = vadiIndex?.let { Swara.fromIndex(it) },
                samvadi = samvadiIndex?.let { Swara.fromIndex(it) },
                isHindustani = true
            )
        }
    }

    private fun loadCarnaticMelakartas(): List<MelaKarta> {
        val jsonString = context.assets.open("carnatic_ragas.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = json.parseToJsonElement(jsonString).jsonArray
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val number = obj["number"]!!.jsonPrimitive.int
            val name = obj["name"]!!.jsonPrimitive.content
            val chakra = obj["chakra"]!!.jsonPrimitive.content
            val swaraIndices = obj["swaras"]!!.jsonArray.map { it.jsonPrimitive.int }
            val swaras = swaraIndices.map { Swara.fromIndex(it) }

            val janyaRagas = obj["janyaRagas"]?.jsonArray?.map { janyaElement ->
                val janyaObj = janyaElement.jsonObject
                val janyaName = janyaObj["name"]!!.jsonPrimitive.content
                val janyaAaroha = janyaObj["aaroha"]!!.jsonArray.map { it.jsonPrimitive.int }
                val janyaAvaroha = janyaObj["avaroha"]!!.jsonArray.map { it.jsonPrimitive.int }
                val janyaSwaras = janyaObj["swaras"]!!.jsonArray.map { it.jsonPrimitive.int }

                Raga(
                    name = janyaName,
                    melaKartaNumber = number,
                    aaroha = janyaAaroha.map { Swara.fromIndex(it) },
                    avaroha = janyaAvaroha.map { Swara.fromIndex(it) },
                    activeSwaras = janyaSwaras.map { Swara.fromIndex(it) }.toSet(),
                    isHindustani = false
                )
            } ?: emptyList()

            MelaKarta(
                number = number,
                name = name,
                chakra = chakra,
                swaras = swaras,
                janyaRagas = janyaRagas
            )
        }
    }
}
