package com.aistudy.aistudy.ask

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun readGeminiErrorJson(body: String): JSONObject = JsonReader(StringReader(body)).use { reader ->
    val error = readErrorValue(reader) as? JSONObject ?: throw JSONException("Invalid error envelope")
    if (reader.peek() != JsonToken.END_DOCUMENT) throw JSONException("Invalid error envelope")
    error
}

private fun readErrorValue(reader: JsonReader): Any = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> JSONObject().apply {
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            put(name, readErrorValue(reader))
        }
        reader.endObject()
    }
    JsonToken.BEGIN_ARRAY -> JSONArray().apply {
        reader.beginArray()
        while (reader.hasNext()) put(readErrorValue(reader))
        reader.endArray()
    }
    // Android JSONObject(String) rounds numeric literals through Double before quota validation.
    JsonToken.NUMBER -> reader.nextString().let { literal ->
        val number = literal.toBigDecimalOrNull()
        // JSONObject rejects Number values outside Double range; preserve those tokens as strings.
        if (number != null && number.toDouble().isFinite()) number else literal
    }
    JsonToken.STRING -> reader.nextString()
    JsonToken.BOOLEAN -> reader.nextBoolean()
    JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
    else -> throw JSONException("Invalid error value")
}
