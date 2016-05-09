package org.gradle.script.lang.kotlin

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.internal.Cast.uncheckedCast
import kotlin.reflect.KProperty

val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties

//region Support for extra delegated properties (val p: String by extensionAware.extra)
// Not really useful in the current setting until Kotlin supports local delegated properties (http://kotlin.link/articles/Kotlin-Post-1-0-Roadmap.html)
operator fun <T> ExtraPropertiesExtension.setValue(nothing: Nothing?, property: KProperty<*>, value: T?) =
    this.set(property.name, value)

operator fun <T> ExtraPropertiesExtension.getValue(nothing: Nothing?, property: KProperty<*>): T? =
    uncheckedCast<T>(this.get(property.name))
//endregion

