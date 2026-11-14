package fr.theworms.infusion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MachineLimitsTest {
    @Test fun `la température est arrondie au pas de 5 entre 45 et 95`() {
        assertEquals(45, MachineLimits.roundTemperature(10))
        assertEquals(45, MachineLimits.roundTemperature(47))
        assertEquals(50, MachineLimits.roundTemperature(48))
        assertEquals(80, MachineLimits.roundTemperature(80))
        assertEquals(95, MachineLimits.roundTemperature(97))
        assertEquals(95, MachineLimits.roundTemperature(99))
    }

    @Test fun `100 et plus donnent le maximum`() {
        assertEquals(MachineLimits.TEMP_MAX, MachineLimits.roundTemperature(100))
        assertEquals(MachineLimits.TEMP_MAX, MachineLimits.roundTemperature(120))
        assertEquals("Max", MachineLimits.temperatureLabel(100))
        assertEquals("80 °C", MachineLimits.temperatureLabel(80))
    }

    @Test fun `l'eau est arrondie au pas de 25 entre 50 et 350`() {
        assertEquals(50, MachineLimits.roundWater(0))
        assertEquals(50, MachineLimits.roundWater(62))
        assertEquals(75, MachineLimits.roundWater(63))
        assertEquals(250, MachineLimits.roundWater(250))
        assertEquals(350, MachineLimits.roundWater(340))
        assertEquals(350, MachineLimits.roundWater(500))
    }

    @Test fun `la liste des températures suit l'ordre de la machine`() {
        assertEquals(12, MachineLimits.temperatures.size)
        assertEquals(45, MachineLimits.temperatures.first())
        assertEquals(95, MachineLimits.temperatures[10])
        assertEquals(100, MachineLimits.temperatures.last())
    }

    @Test fun `la durée s'affiche en minutes et secondes`() {
        assertEquals("1:30", MachineLimits.steepLabel(90))
        assertEquals("0:15", MachineLimits.steepLabel(15))
        assertEquals("10:00", MachineLimits.steepLabel(600))
    }

    @Test fun `unités impériales comme l'écran de la machine`() {
        assertEquals("80 °C", MachineLimits.temperatureLabel(80))
        assertEquals("176 °F", MachineLimits.temperatureLabel(80, metric = false))
        assertEquals("113 °F", MachineLimits.temperatureLabel(45, metric = false))
        assertEquals("Max", MachineLimits.temperatureLabel(100, metric = false))
        assertEquals("250 ml", MachineLimits.waterLabel(250))
        assertEquals("2 oz", MachineLimits.waterLabel(50, metric = false))
        assertEquals("9 oz", MachineLimits.waterLabel(250, metric = false))
        assertEquals("9,5 oz", MachineLimits.waterLabel(275, metric = false, comma = true))
        assertEquals("9.5 oz", MachineLimits.waterLabel(275, metric = false, comma = false))
        assertEquals("12 oz", MachineLimits.waterLabel(350, metric = false))
    }
}
