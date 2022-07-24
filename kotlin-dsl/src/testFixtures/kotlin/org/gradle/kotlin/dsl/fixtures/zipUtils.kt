package org.gradle.kotlin.dsl.fixtures

import kotlin.reflect.KClass


fun classEntriesFor(classes: Array<out KClass<*>>): Sequence<Pair<String, ByteArray>> =
    classes.asSequence().map { classEntryFor(it) }


fun classEntriesFor(vararg classes: Class<*>): Sequence<Pair<String, ByteArray>> =
    classes.asSequence().map { classEntryFor(it) }


fun classEntryFor(clazz: KClass<*>): Pair<String, ByteArray> =
    classEntryFor(clazz.java)


fun classEntryFor(clazz: Class<*>): Pair<String, ByteArray> {
    val classFilePath = clazz.name.replace('.', '/') + ".class"
    return classFilePath to clazz.getResource("/$classFilePath").readBytes()
}
