package io.wenyou.textquest

import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.SexualOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The built-in stories give every named orientation at least one character. */
class SpectrumPresetTest {
    private val presets = listOf(File("src/alpha/assets/presets"), File("app/src/alpha/assets/presets")).first(File::isDirectory)
    private fun bundle(name: String) = AppJson.decodeFromString(AppBundle.serializer(), File(presets, name).readText())

    @Test fun everyNamedOrientationHasABuiltInCharacter() {
        val all = presets.listFiles { f -> f.extension == "json" }!!.flatMap { bundle(it.name).characters }
        val missing = SexualOrientation.entries.toSet() - SexualOrientation.UNKNOWN - all.map { it.orientation }.toSet()
        assertEquals(emptySet<SexualOrientation>(), missing)
    }

    @Test fun spectrumPackIsAllAgesLgbtAndSelfContained() {
        val pack = bundle("wenyou-spectrum-presets.json")
        assertEquals(7, pack.stories.size)
        val ids = pack.characters.map { it.id }.toSet()
        assertTrue(pack.characters.all { it.lgbt && !it.adult && it.initial.metrics.isNotEmpty() && it.orientation != SexualOrientation.UNKNOWN })
        assertTrue(pack.stories.all { it.lgbt && !it.adult && it.characterIds.isNotEmpty() && ids.containsAll(it.characterIds) })
    }
}
