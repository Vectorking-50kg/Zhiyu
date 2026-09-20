package funapp.ctrlcv.zhiyu.core.data.notification

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformAlertStateTest {
    @Test fun repeatedRoundTripsPreserveFloatValuesAndStableFieldNames() {
        val state = PlatformAlertState(mapOf("five_hour" to 82.5f, "seven_day" to 100f, "extra" to 0f), 1)
        val json = PlatformAlertStateCodec.encode(state)
        assertEquals(setOf("percents", "notifiedLevel"), JsonParser.parseString(json).asJsonObject.keySet())

        val first = PlatformAlertStateCodec.decode(json)!!
        val second = PlatformAlertStateCodec.decode(PlatformAlertStateCodec.encode(first))!!
        assertEquals(state, second)
        // This lookup formerly threw in release when Gson reconstructed a raw map of Doubles.
        assertEquals(82.5f, second.percents.getValue("five_hour"), 0f)
    }

    @Test fun readsExistingUnobfuscatedState() {
        val state = PlatformAlertStateCodec.decode("""{"percents":{"5 小时":91.25,"周限额":63},"notifiedLevel":2}""")!!
        assertEquals(mapOf("5 小时" to 91.25f, "周限额" to 63f), state.percents)
        assertEquals(2, state.notifiedLevel)
        assertEquals(PlatformAlertState(emptyMap(), 0), PlatformAlertStateCodec.decode("""{"percents":{},"notifiedLevel":0}"""))
    }

    @Test fun discardsObfuscatedStateAndAllowsNextObservationToReplaceIt() {
        assertNull(PlatformAlertStateCodec.decode("""{"a":{"five_hour":90.0},"b":1}"""))
        val fresh = PlatformAlertState(mapOf("five_hour" to 12f), 0)
        assertEquals(fresh, PlatformAlertStateCodec.decode(PlatformAlertStateCodec.encode(fresh)))
    }

    @Test fun malformedStateCannotEscapeIntoQuotaResetComparison() {
        val invalid = listOf(
            null, "", "not-json", "null", "[]", "{}",
            """{"percents":null,"notifiedLevel":1}""",
            """{"percents":[],"notifiedLevel":1}""",
            """{"percents":{"five_hour":null},"notifiedLevel":1}""",
            """{"percents":{"five_hour":"90"},"notifiedLevel":1}""",
            """{"percents":{"five_hour":{}},"notifiedLevel":1}""",
            """{"percents":{"five_hour":1e999},"notifiedLevel":1}""",
            """{"percents":{"five_hour":-1},"notifiedLevel":1}""",
            """{"percents":{"five_hour":100.000001},"notifiedLevel":1}""",
            """{"percents":{"five_hour":90}}""",
            """{"percents":{"five_hour":90},"notifiedLevel":null}""",
            """{"percents":{"five_hour":90},"notifiedLevel":"1"}""",
            """{"percents":{"five_hour":90},"notifiedLevel":1.5}""",
            """{"percents":{"five_hour":90},"notifiedLevel":-1}""",
            """{"percents":{"five_hour":90},"notifiedLevel":3}""",
        )
        invalid.forEach { json -> assertNull("Must discard $json", PlatformAlertStateCodec.decode(json)) }
    }
}
