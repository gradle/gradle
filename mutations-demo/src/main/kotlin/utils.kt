package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import java.util.*
import kotlin.reflect.KFunction

fun DataClass.propertyFromGetter(kFunction: KFunction<*>) =
    propertyNamed(kFunction.name.substringAfter("get").replaceFirstChar { it.lowercase(Locale.getDefault()) })