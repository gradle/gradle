/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.extensibility.DefaultExtensionsSchema
import org.gradle.internal.extensions.stdlib.capitalized

internal class CrossProjectConfigurationReportingGradleExtensionsContainer(
    private val delegate: ExtensionContainerInternal,
    private val referrer: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : ExtensionContainerInternal {

    private fun onMutableStateAccess() {
        ipProblems.report {
            problem {
                text("Project ")
                reference(referrer.buildTreePath)
                text(" cannot access Gradle.extensions")
            }
                .exception { message -> message.capitalized() }
                .build()
        }
    }

    override fun getAsMap(): Map<String, Any> = ImmutableMap.copyOf(delegate.getAsMap())

    override fun getExtensionsSchema(): ExtensionsSchema = DefaultExtensionsSchema.create(delegate.extensionsSchema)

    override fun <T : Any> getByType(type: Class<T>): T = getByType(TypeOf.typeOf(type))

    override fun <T : Any> getByType(type: TypeOf<T>): T {
        if (!Workarounds.canAccessGradleExtensionFromProjectScope(type)) {
            onMutableStateAccess()
        }
        return delegate.getByType(type)
    }

    override fun <T : Any> findByType(type: Class<T>): T? = findByType(TypeOf.typeOf(type))

    override fun <T : Any> findByType(type: TypeOf<T>): T? {
        onMutableStateAccess()
        return delegate.findByType(type)
    }

    override fun getByName(name: String): Any {
        onMutableStateAccess()
        return delegate.getByName(name)
    }

    override fun findByName(name: String): Any? {
        onMutableStateAccess()
        return delegate.findByName(name)
    }

    override fun getExtraProperties(): ExtraPropertiesExtension {
        onMutableStateAccess()
        return delegate.extraProperties
    }

    override fun <T : Any> add(publicType: Class<T>, name: String, extension: T) {
        add(TypeOf.typeOf(publicType), name, extension)
    }

    override fun <T : Any> add(publicType: TypeOf<T>, name: String, extension: T) {
        onMutableStateAccess()
        delegate.add(publicType, name, extension)
    }

    override fun add(name: String, extension: Any) {
        onMutableStateAccess()
        delegate.add(name, extension)
    }

    override fun <T : Any> create(
        publicType: Class<T>,
        name: String,
        instanceType: Class<out T>,
        vararg constructionArguments: Any
    ): T = create(TypeOf.typeOf(publicType), name, instanceType, *constructionArguments)

    override fun <T : Any> create(
        publicType: TypeOf<T>,
        name: String,
        instanceType: Class<out T>,
        vararg constructionArguments: Any
    ): T {
        onMutableStateAccess()
        return delegate.create(publicType, name, instanceType, *constructionArguments)
    }

    override fun <T : Any> create(name: String, type: Class<T>, vararg constructionArguments: Any): T {
        onMutableStateAccess()
        return delegate.create(name, type, *constructionArguments)
    }

    override fun <T : Any> configure(type: Class<T>, action: Action<in T>) {
        configure(TypeOf.typeOf(type), action)
    }

    override fun <T : Any> configure(type: TypeOf<T>, action: Action<in T>) {
        onMutableStateAccess()
        delegate.configure(type, action)
    }

    override fun <T : Any> configure(name: String, action: Action<in T>) {
        onMutableStateAccess()
        delegate.configure(name, action)
    }

    // region Groovy support
    fun propertyMissing(name: String): Any =
        getByName(name)

    fun propertyMissing(name: String, value: Any) {
        require(delegate.findByName(name) == null) {
            "There's an extension registered with name '$name'. You should not reassign it via a property setter."
        }
        add(name, value)
    }
    // endregion Groovy support
}
