package fr.theworms.infusion.domain

/**
 * Plages acceptées par la Maker One, lues dans BRU Connect 1.13.0 :
 * eau de 50 à 350 ml par 25 ml, température de 45 à 95 °C par 5 °C puis « Maximum »,
 * 10 préréglages, surnom de 12 caractères.
 */
object MachineLimits {
    const val TEMP_MIN = 45
    const val TEMP_MAX_SETTABLE = 95
    /** « Maximum » (ébullition) : la machine ne prend pas de valeur entre 95 et le maximum. */
    const val TEMP_MAX = 100
    const val TEMP_STEP = 5

    const val WATER_MIN = 50
    const val WATER_MAX = 350
    const val WATER_STEP = 25

    const val STEEP_MIN_S = 10
    const val STEEP_MAX_S = 3600

    const val NAME_MAX = 12
    const val PRESETS = 10

    /** Arrondit au pas de 5 °C le plus proche ; 100 et plus = « Maximum ». */
    fun roundTemperature(celsius: Int): Int {
        if (celsius >= TEMP_MAX) return TEMP_MAX
        val clamped = celsius.coerceIn(TEMP_MIN, TEMP_MAX_SETTABLE)
        return ((clamped + TEMP_STEP / 2) / TEMP_STEP * TEMP_STEP).coerceIn(TEMP_MIN, TEMP_MAX_SETTABLE)
    }

    /** Arrondit au pas de 25 ml le plus proche, entre 50 et 350 ml. */
    fun roundWater(ml: Int): Int {
        val clamped = ml.coerceIn(WATER_MIN, WATER_MAX)
        return ((clamped - WATER_MIN + WATER_STEP / 2) / WATER_STEP * WATER_STEP + WATER_MIN).coerceIn(WATER_MIN, WATER_MAX)
    }

    /** Arrondit au multiple de 50 ml le plus proche (machine qui ne connaît pas les demi-pas). */
    fun roundWater50(ml: Int): Int = ((ml.coerceIn(WATER_MIN, WATER_MAX) + 25) / 50 * 50).coerceIn(WATER_MIN, WATER_MAX)

    /**
     * Les 110 durées de la machine, dans l'ordre de leurs index : 10 à 50 s par 10 s, 1:00 à 9:50 par
     * 10 s, puis 10 à 60 min par minute.
     */
    val steepDurations: List<Int> = buildList {
        for (s in 10..50 step 10) add(s)
        for (m in 1..9) for (k in 0..5) add(60 * m + 10 * k)
        for (m in 10..60) add(60 * m)
    }

    /** Index de la durée de la machine la plus proche. */
    fun steepIndex(seconds: Int): Int {
        val s = seconds.coerceIn(STEEP_MIN_S, STEEP_MAX_S)
        return steepDurations.indices.minBy { kotlin.math.abs(steepDurations[it] - s) }
    }

    /** Durée de la machine la plus proche. */
    fun roundSteep(seconds: Int): Int = steepDurations[steepIndex(seconds)]

    /** Valeurs de la machine, dans l'ordre : 45, 50, …, 95, puis 100 (« Maximum »). */
    val temperatures: List<Int> = (TEMP_MIN..TEMP_MAX_SETTABLE step TEMP_STEP).toList() + TEMP_MAX

    /** « 80 °C », ou « 176 °F » en unités impériales ; « Max » pour le maximum. */
    fun temperatureLabel(celsius: Int, metric: Boolean = true): String = when {
        celsius >= TEMP_MAX -> "Max"
        metric -> "$celsius °C"
        else -> "${Math.round(celsius * 9 / 5.0 + 32)} °F"
    }

    /**
     * « 250 ml », ou en onces comme l'écran de la machine (BRU Connect : 50 ml = 2 oz … 350 ml = 12 oz) ;
     * les demi-pas de 25 ml prennent la moyenne de leurs voisins (275 ml = 9,5 oz).
     */
    fun waterLabel(ml: Int, metric: Boolean = true, comma: Boolean = java.util.Locale.getDefault().language != "en"): String {
        if (metric) return "$ml ml"
        val w = roundWater(ml)
        val lo = OUNCES[(w / 50 - 1).coerceIn(0, OUNCES.lastIndex)]
        val oz = if (w % 50 == 0) lo.toDouble() else (lo + OUNCES[(w / 50).coerceAtMost(OUNCES.lastIndex)]) / 2.0
        // Virgule décimale en français, point en anglais (la langue de l'app).
        return (if (oz % 1.0 == 0.0) oz.toInt().toString() else oz.toString().let { if (comma) it.replace('.', ',') else it }) + " oz"
    }

    /** Onces affichées par BRU pour 50, 100, … 350 ml. */
    private val OUNCES = listOf(2, 3, 5, 7, 9, 10, 12)

    fun steepLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
