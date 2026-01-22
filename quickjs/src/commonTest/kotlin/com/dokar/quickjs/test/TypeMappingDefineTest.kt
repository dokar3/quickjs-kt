package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

// https://github.com/dokar3/quickjs-kt/issues/127
class TypeMappingDefineTest {
    data class GeoLocation(val lat: Double, val lon: Double)

    object GeoLocationConverter : JsObjectConverter<GeoLocation> {
        override val targetType: KType = typeOf<GeoLocation>()

        override fun convertToTarget(value: JsObject): GeoLocation {
            return GeoLocation(
                lat = value["lat"] as Double,
                lon = value["lon"] as Double
            )
        }

        override fun convertToSource(value: GeoLocation): JsObject {
            return mapOf(
                "lat" to value.lat,
                "lon" to value.lon
            ).toJsObject()
        }
    }

    @Test
    fun testGlobalTypeConverter() = runTest {
        quickJs {
            addTypeConverters(GeoLocationConverter)
            asyncFunction<GeoLocation>("gps") {
                GeoLocation(123.0, -123.0)
            }
            val result = evaluate<Double>("""let foo = await gps(); foo.lat""")
            assertEquals(123.0, result)
        }
    }

    @Test
    fun testNestedTypeConverter() = runTest {
        quickJs {
            addTypeConverters(GeoLocationConverter)
            define("host") {
                asyncFunction<GeoLocation>("gps") {
                    GeoLocation(123.0, -123.0)
                }
            }
            val result = evaluate<Double>("""let foo = await host.gps(); foo.lat""")
            assertEquals(123.0, result)
        }
    }
}
