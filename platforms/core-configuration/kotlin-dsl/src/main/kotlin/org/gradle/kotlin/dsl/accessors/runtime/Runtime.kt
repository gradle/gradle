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

package org.gradle.kotlin.dsl.accessors.runtime

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.Factory
import org.gradle.internal.declarativedsl.software.getSoftwareFeatureModelInstance
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.mapOfNonNullValuesOf
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.uncheckedCast
import org.gradle.plugin.software.internal.SoftwareFeatureApplicator
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry


fun extensionOf(target: Any, extensionName: String): Any =
    (target as ExtensionAware).extensions.getByName(extensionName)


fun conventionPluginOf(target: Any, name: String) =
    @Suppress("deprecation")
    conventionPluginByName(conventionOf(target), name)


@Suppress("deprecation")
fun conventionPluginByName(convention: org.gradle.api.plugins.Convention, name: String): Any =
    convention.plugins[name] ?: error("A convention named '$name' could not be found.")


@Suppress("deprecation")
fun conventionOf(target: Any): org.gradle.api.plugins.Convention = when (target) {
    is Project -> DeprecationLogger.whileDisabled(Factory { target.convention })!!
    is org.gradle.api.internal.HasConvention -> DeprecationLogger.whileDisabled(Factory { target.convention })!!
    else -> error("Object `$target` doesn't support conventions!")
}


fun <T : Dependency> addDependencyTo(
    dependencies: DependencyHandler,
    configuration: String,
    dependencyNotation: Any,
    configurationAction: Action<T>
): T = dependencies.run {
    uncheckedCast<T>(create(dependencyNotation)).also { dependency ->
        configurationAction.execute(dependency)
        add(configuration, dependency)
    }
}


fun addConfiguredDependencyTo(
    dependencies: DependencyHandler,
    configuration: String,
    dependencyNotation: Provider<*>,
    configurationAction: Action<ExternalModuleDependency>
) {
    dependencies.addProvider(configuration, dependencyNotation, configurationAction)
}


fun addConfiguredDependencyTo(
    dependencies: DependencyHandler,
    configuration: String,
    dependencyNotation: ProviderConvertible<*>,
    configurationAction: Action<ExternalModuleDependency>
) {
    dependencies.addProviderConvertible(configuration, dependencyNotation, configurationAction)
}


@Suppress("LongParameterList")
fun addExternalModuleDependencyTo(
    dependencyHandler: DependencyHandler,
    targetConfiguration: String,
    group: String,
    name: String,
    version: String?,
    configuration: String?,
    classifier: String?,
    ext: String?,
    action: Action<ExternalModuleDependency>?
): ExternalModuleDependency = externalModuleDependencyFor(
    dependencyHandler,
    group,
    name,
    version,
    configuration,
    classifier,
    ext
).also {
    action?.execute(it)
    dependencyHandler.add(targetConfiguration, it)
}


fun externalModuleDependencyFor(
    dependencyHandler: DependencyHandler,
    group: String,
    name: String,
    version: String?,
    configuration: String?,
    classifier: String?,
    ext: String?
): ExternalModuleDependency = dependencyHandler.create(
    mapOfNonNullValuesOf(
        "group" to group,
        "name" to name,
        "version" to version,
        "configuration" to configuration,
        "classifier" to classifier,
        "ext" to ext
    )
) as ExternalModuleDependency


@Suppress("unused") // invoked from generated bytecode
fun <T : Any> maybeRegister(
    container: NamedDomainObjectContainer<T>,
    name: String,
    configure: Action<in T>
) {
    with(container) {
        if (name in names) {
            named(name, configure)
        } else {
            register(name, configure)
        }
    }
}


@Suppress("unused") // invoked from generated bytecode
fun applySoftwareFeature(
    project: Project,
    name: String,
    configure: Action<in Any>
) {
    val softwareFeature = project.serviceOf<SoftwareFeatureRegistry>().softwareFeatureImplementations.getValue(name)
    project.serviceOf<SoftwareFeatureApplicator>().applyFeatureTo(project, softwareFeature)
    configure.invoke(getSoftwareFeatureModelInstance(softwareFeature, project as ProjectInternal))
}


fun functionToAction(f: (Any?) -> Unit): Action<Any> =
    Action { f(this) }
