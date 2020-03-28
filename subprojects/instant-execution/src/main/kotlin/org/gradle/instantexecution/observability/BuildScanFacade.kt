/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.observability

import org.gradle.api.plugins.ExtensionAware
import org.gradle.instantexecution.InstantExecutionHost
import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.internal.scan.config.BuildScanPluginApplied
import org.gradle.kotlin.dsl.*
import kotlin.reflect.KProperty


internal
interface BuildScanFacade {

    var enterpriseServer: String?
    var termsOfServiceUrl: String?
    var termsOfServiceAgree: String?
    var server: String?
    var isAllowUntrustedServer: Boolean
    var isCaptureTaskInputFiles: Boolean
    // TODO not accessible // var isPublishAlways: Boolean
    // TODO not accessible // var isPublishOnFailure: Boolean
}


internal
class DefaultBuildScanFacade(
    private val host: InstantExecutionHost,
    private val buildScanPluginApplied: BuildScanPluginApplied
) : BuildScanFacade {

    private
    val gradleEnterprise: Any by unsafeLazy {
        host.currentBuild.gradle.settings.extensionNamed("gradleEnterprise")
    }

    private
    val buildScan: Any by unsafeLazy {
        gradleEnterprise.withGroovyBuilder { getProperty("buildScan") }
    }

    override var enterpriseServer: String?
        by stringGroovyProperty("server") { gradleEnterprise }

    override var termsOfServiceUrl: String?
        by stringGroovyProperty { buildScan }

    override var termsOfServiceAgree: String?
        by stringGroovyProperty { buildScan }

    override var server: String?
        by stringGroovyProperty { buildScan }

    override var isAllowUntrustedServer: Boolean
        by booleanGroovyProperty("allowUntrustedServer") { buildScan }

    override var isCaptureTaskInputFiles: Boolean
        by booleanGroovyProperty("captureTaskInputFiles") { buildScan }

    private
    val isBuildScanEnabled: Boolean
        get() = buildScanPluginApplied.isBuildScanPluginApplied

    private
    fun Any.extensionNamed(name: String): Any =
        (this as ExtensionAware).extensions.getByName(name)

    private
    fun stringGroovyProperty(name: String? = null, delegate: () -> Any) =
        GroovyPropertyDelegate(name, delegate) { it as? String }

    private
    fun booleanGroovyProperty(name: String? = null, delegate: () -> Any) =
        GroovyPropertyDelegate(name, delegate) { it as? Boolean ?: false }

    private
    inner class GroovyPropertyDelegate<T : Any?>(

        private
        val name: String? = null,

        private
        val delegate: () -> Any,

        private
        val transform: (Any?) -> T
    ) {

        operator fun getValue(thisRef: Any, property: KProperty<*>): T =
            if (isBuildScanEnabled) delegate().withGroovyBuilder {
                transform(getProperty(name ?: property.name))
            }
            else transform(null)

        operator fun setValue(thisRef: Any, property: KProperty<*>, value: Any?) =
            if (isBuildScanEnabled) delegate().withGroovyBuilder {
                setProperty(name ?: property.name, transform(value))
            }
            else Unit
    }
}
