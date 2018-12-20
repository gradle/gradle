package org.gradle.kotlin.dsl.fixtures

import org.gradle.kotlin.dsl.support.zipTo

import java.io.File

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


fun jarWithPluginDescriptors(file: File, vararg pluginIdsToImplClasses: Pair<String, String>) =
    file.also {
        zipTo(it, pluginIdsToImplClasses.asSequence().map { (id, implClass) ->
            pluginDescriptorEntryFor(id, implClass)
        })
    }


fun pluginDescriptorEntryFor(id: String, implClass: String) =
    "META-INF/gradle-plugins/$id.properties" to "implementation-class=$implClass".toByteArray()
