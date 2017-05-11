package org.gradle.script.lang.kotlin.fixtures

import org.gradle.script.lang.kotlin.support.zipTo
import java.io.ByteArrayOutputStream

fun zipOf(entries: Sequence<Pair<String, ByteArray>>): ByteArray =
    ByteArrayOutputStream().run {
        zipTo(this, entries)
        toByteArray()
    }

fun classEntriesFor(vararg classes: Class<*>): Sequence<Pair<String, ByteArray>> =
    classes.asSequence().map {
        val classFilePath = it.name.replace('.', '/') + ".class"
        classFilePath to it.getResource("/$classFilePath").readBytes()
    }
