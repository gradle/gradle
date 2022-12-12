/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.deprecation.DeprecationLogger.deprecateProperty

import org.gradle.kotlin.dsl.*


/**
 * Options for the `kotlin-dsl` plugin.
 *
 * @see KotlinDslPlugin
 */
abstract class KotlinDslPluginOptions internal constructor(objects: ObjectFactory) {

    @Deprecated("Configure a Java Toolchain instead")
    internal
    val jvmTargetProperty = objects.property<String>()

    /**
     * Kotlin compilation JVM target.
     *
     * Defaults to `1.8`.
     *
     * @see [org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.jvmTarget]
     */
    @Deprecated("Configure a Java Toolchain instead")
    val jvmTarget: Property<String> = DeprecatedProperty(jvmTargetProperty)
}


private
class DeprecatedProperty(private val delegate: Property<String>) : Property<String> by delegate {

    override fun get(): String {
        nagUserAboutJvmTarget()
        return delegate.get()
    }

    override fun getOrElse(defaultValue: String): String {
        nagUserAboutJvmTarget()
        return delegate.getOrElse(defaultValue)
    }

    override fun getOrNull(): String? {
        nagUserAboutJvmTarget()
        return delegate.orNull
    }

    override fun isPresent(): Boolean {
        nagUserAboutJvmTarget()
        return delegate.isPresent
    }

    override fun set(value: String?) {
        nagUserAboutJvmTarget()
        delegate.set(value)
    }

    override fun set(provider: Provider<out String>) {
        nagUserAboutJvmTarget()
        delegate.set(provider)
    }

    override fun value(value: String?): Property<String> {
        nagUserAboutJvmTarget()
        return delegate.value(value)
    }

    override fun value(provider: Provider<out String>): Property<String> {
        nagUserAboutJvmTarget()
        return delegate.value(provider)
    }

    override fun convention(value: String?): Property<String> {
        nagUserAboutJvmTarget()
        return delegate.convention(value)
    }

    override fun convention(provider: Provider<out String>): Property<String> {
        nagUserAboutJvmTarget()
        return delegate.convention(provider)
    }

    override fun finalizeValue() {
        nagUserAboutJvmTarget()
        delegate.finalizeValue()
    }

    override fun finalizeValueOnRead() {
        nagUserAboutJvmTarget()
        delegate.finalizeValueOnRead()
    }

    override fun disallowChanges() {
        nagUserAboutJvmTarget()
        delegate.disallowChanges()
    }

    override fun disallowUnsafeRead() {
        nagUserAboutJvmTarget()
        delegate.disallowUnsafeRead()
    }

    private
    fun nagUserAboutJvmTarget() {
        deprecateProperty(KotlinDslPluginOptions::class.java, "jvmTarget")
            .withAdvice("Configure a Java Toolchain instead.")
            .willBeRemovedInGradle9()
            .withUserManual("kotlin_dsl", "sec:kotlin-dsl_plugin")
            .nagUser()
    }
}


internal
fun Project.kotlinDslPluginOptions(action: KotlinDslPluginOptions.() -> Unit) =
    configure(action)
