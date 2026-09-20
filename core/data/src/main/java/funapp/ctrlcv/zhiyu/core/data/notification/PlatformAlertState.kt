package funapp.ctrlcv.zhiyu.core.data.notification

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Per-platform notification deduplication; independent of the last successful usage snapshot. */
internal data class PlatformAlertState(
    val percents: Map<String, Float>,
    val notifiedLevel: Int,
)

/** Explicit field names and numeric conversion keep persisted state independent of R8 reflection. */
internal object PlatformAlertStateCodec {
    fun encode(state: PlatformAlertState): String {
        require(state.notifiedLevel in 0..2)
        val percents = JsonObject()
        state.percents.forEach { (window, percent) ->
            require(percent.isFinite() && percent in 0f..100f)
            percents.addProperty(window, percent)
        }
        return JsonObject().apply {
            add("percents", percents)
            addProperty("notifiedLevel", state.notifiedLevel)
        }.toString()
    }

    fun decode(json: String?): PlatformAlertState? {
        if (json == null) return null
        return try {
            val root = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
                ?: return null
            val percents = root.get("percents")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return null
            val level = root.get("notifiedLevel")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asString?.toIntOrNull()?.takeIf { it in 0..2 } ?: return null
            val values = percents.entrySet().associate { (window, value) ->
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
                val percent = value.asDouble
                if (!percent.isFinite() || percent !in 0.0..100.0) return null
                window to percent.toFloat()
            }
            PlatformAlertState(values, level)
        } catch (_: Exception) {
            null
        }
    }
}
