package org.gradle.kotlin.dsl.fixtures

import kotlin.reflect.KClass


fun classEntriesFor(classes: Array<out KClass<*>>) =
    classEntriesFor(*classes.map { it.java }.toTypedArray())


fun classEntriesFor(vararg classes: Class<*>): Sequence<Pair<String, ByteArray>> =
    classes.asSequence().map {
        val classFilePath = it.name.replace('.', '/') + ".class"
        classFilePath to it.getResource("/$classFilePath").readBytes()
    }
