package ru.yole.jkid.serialization

import ru.yole.jkid.*
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

fun serialize(obj: Any): String = buildString { serializeObject(obj) }

/* the first implementation discussed in the book */
private fun StringBuilder.serializeObjectWithoutAnnotation(obj: Any) {
    val kClass = obj.javaClass.kotlin
    val properties = kClass.memberProperties

    properties.joinToStringBuilder(this, prefix = "{", postfix = "}") { prop ->
        serializeString(prop.name)
        append(": ")
        serializePropertyValue(prop.get(obj))
    }
}

private fun StringBuilder.serializeObject(obj: Any) {
    obj.javaClass.kotlin.memberProperties
            .filter { it.findAnnotation<JsonExclude>() == null }
            .joinToStringBuilder(this, prefix = "{", postfix = "}") {
                serializeProperty(it, obj)
            }
}

private fun StringBuilder.serializeProperty(
        prop: KProperty1<Any, *>, obj: Any
) {
    val jsonNameAnn = prop.findAnnotation<JsonName>()
    val propName = jsonNameAnn?.name ?: prop.name
    serializeString(propName)
    append(": ")

    val value = prop.get(obj)
    var jsonValue: Any? = null
    val dateFormatAnn = prop.findAnnotation<DateFormat>()
    if (dateFormatAnn != null) {
        if (value is Date) {
            jsonValue = DateSerializer(dateFormatAnn.format).toJsonValue(value)
        } else {
            throw IllegalStateException("DateFormat annotation is used but the value is not Date")
        }
    }else{
        val serializer = prop.getSerializer()
        val jsonValue = prop.getSerializer()?.toJsonValue(value)
    }
    serializePropertyValue(jsonValue?: value)
}

fun KProperty<*>.getSerializer(): ValueSerializer<Any?>? {
    val customSerializerAnn = findAnnotation<CustomSerializer>() ?: return null
    val serializerClass = customSerializerAnn.serializerClass

    val valueSerializer = serializerClass.objectInstance
            ?: serializerClass.createInstance()
    @Suppress("UNCHECKED_CAST")
    return valueSerializer as ValueSerializer<Any?>
}

private fun StringBuilder.serializePropertyValue(value: Any?) {
    when (value) {
        null -> append("null")
        is String -> serializeString(value)
        is Number, is Boolean -> append(value.toString())
        is List<*> -> serializeList(value)
        is Map<*,*> -> serializeMap(value)
        else -> serializeObject(value)
    }
}

private fun StringBuilder.serializeList(data: List<Any?>) {
    data.joinToStringBuilder(this, prefix = "[", postfix = "]") {
        serializePropertyValue(it)
    }
}

//Support only Map<String, *>
private fun StringBuilder.serializeMap(data: Map<*, *>){
    data.flatMap { (k,v) ->
        var builder = StringBuilder()
        if(k !is String ) throw IllegalStateException()
        builder.serializeString(k)
        builder.append(": ")
        builder.serializePropertyValue(v)
        mutableListOf(builder.toString())
    }.joinToStringBuilder(this, prefix = "{", postfix = "}")
}

private fun StringBuilder.serializeString(s: String) {
    append('\"')
    s.forEach { append(it.escape()) }
    append('\"')
}

private fun Char.escape(): Any =
        when (this) {
            '\\' -> "\\\\"
            '\"' -> "\\\""
            '\b' -> "\\b"
            '\u000C' -> "\\f"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> this
        }
