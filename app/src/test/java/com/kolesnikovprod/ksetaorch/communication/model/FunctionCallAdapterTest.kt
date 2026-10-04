package com.kolesnikovprod.ksetaorch.communication.model

import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.FunctionCallAdapter
import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.toFunctionJson
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Native-адаптер не исполняет функции и сохраняет типы данных SDK.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class FunctionCallAdapterTest {
    @Test fun `empty function has proper native object schema and no executable bridge`() {
        val adapter = FunctionCallAdapter(KsenaxModelFunctionDeclaration("torch_on", "Light"))
        val json = Json.parseToJsonElement(adapter.getToolDescriptionJsonString()).jsonObject
        assertEquals("object", json["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertThrows(IllegalStateException::class.java) { adapter.execute("{}") }
    }
    @Test fun `native integral numbers strings and booleans keep their types`() {
        assertEquals("""{"count":5,"hours":1.5,"label":"утро","flag":false}""", mapOf("count" to 5.0, "hours" to 1.5, "label" to "утро", "flag" to false).toFunctionJson().toString())
        assertThrows(IllegalArgumentException::class.java) { Double.NaN.toFunctionJson() }
    }
}
