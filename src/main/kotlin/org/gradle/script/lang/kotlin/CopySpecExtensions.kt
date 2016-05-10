package org.gradle.script.lang.kotlin

import org.gradle.api.file.CopySpec
import java.io.FilterReader

// Interim extensions until those methods are added to CopySpec
fun CopySpec.from(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    from(path).apply { setup(this) }

fun CopySpec.into(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    into(path).apply { setup(this) }

inline fun <reified T : FilterReader> CopySpec.filter(vararg properties: Pair<String, *>) =
    filter(mapOf(*properties), T::class.java)

inline fun <reified T : FilterReader> CopySpec.filter(properties: Map<String, *>) =
    filter(properties, T::class.java)
