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

internal class CrossProjectConfigurationReportingExtensionsContainer(
    private val delegate: ExtensionContainerInternal,
    private val referrer: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : ExtensionContainerInternal by delegate {

    private fun onMutableStateAccess(what: String) {
        ipProblems.report {
            problem {
                text("Project ")
                reference(referrer.buildTreePath)
                text(" cannot $what on Gradle extension container")
            }
                .exception { message -> message.capitalized() }
                .build()
        }
    }

    override fun getAsMap(): Map<String, Any> = ImmutableMap.copyOf(delegate.getAsMap())

    override fun getExtensionsSchema(): ExtensionsSchema = DefaultExtensionsSchema.create(delegate.extensionsSchema)

    override fun getExtraProperties(): ExtraPropertiesExtension =
        CrossProjectConfigurationReportingExtraPropertiesExtension(delegate.extraProperties, referrer, ipProblems)

    override fun <T : Any> add(publicType: Class<T>, name: String, extension: T) {
        onMutableStateAccess("add extension `$name` with public type `${publicType.simpleName}`")
        delegate.add(publicType, name, extension)
    }

    override fun <T : Any> add(publicType: TypeOf<T>, name: String, extension: T) {
        onMutableStateAccess("add extension `$name` with public type `${publicType.simpleName}`")
        delegate.add(publicType, name, extension)
    }

    override fun add(name: String, extension: Any) {
        onMutableStateAccess("add extension `$name` with public type `${extension.javaClass.simpleName}`")
        delegate.add(name, extension)
    }

    override fun <T : Any> create(
        publicType: Class<T>,
        name: String,
        instanceType: Class<out T>,
        vararg constructionArguments: Any
    ): T {
        onMutableStateAccess("create extension `$name` with public type `${publicType.simpleName}`")
        return delegate.create(publicType, name, instanceType, *constructionArguments)
    }

    override fun <T : Any> create(
        publicType: TypeOf<T>,
        name: String,
        instanceType: Class<out T>,
        vararg constructionArguments: Any
    ): T {
        onMutableStateAccess("create extension `$name` with public type `${publicType.simpleName}`")
        return delegate.create(publicType, name, instanceType, *constructionArguments)
    }

    override fun <T : Any> create(name: String, type: Class<T>, vararg constructionArguments: Any): T {
        onMutableStateAccess("create extension `$name` with public type `${type.simpleName}`")
        return delegate.create(name, type, *constructionArguments)
    }

    override fun <T : Any> configure(type: Class<T>, action: Action<in T>) {
        onMutableStateAccess("configure extension of type `${type.name}`")
        delegate.configure(type, action)
    }

    override fun <T : Any> configure(type: TypeOf<T>, action: Action<in T>) {
        onMutableStateAccess("configure extension of type `${type}`")
        delegate.configure(type, action)
    }

    override fun <T : Any> configure(name: String, action: Action<in T>) {
        onMutableStateAccess("configure extension of name `${name}`")
        delegate.configure(name, action)
    }
}

private class CrossProjectConfigurationReportingExtraPropertiesExtension(
    private val delegate: ExtraPropertiesExtension,
    private val referrer: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : ExtraPropertiesExtension by delegate {

    override fun set(name: String, value: Any?) {
        ipProblems.report {
            problem {
                text("Project ")
                reference(referrer.buildTreePath)
                text(" cannot set extra properties extension `$name` on Gradle extension container")
            }
                .exception { message -> message.capitalized() }
                .build()
        }
        delegate.set(name, value)
    }
}
