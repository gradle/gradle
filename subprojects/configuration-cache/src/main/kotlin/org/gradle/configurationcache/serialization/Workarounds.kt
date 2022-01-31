/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache.serialization

import java.lang.reflect.Field


internal
object Workarounds {

    private
    val ignoredBeanFields: List<Pair<String, String>> = listOf(
        // TODO:configuration-cache remove once fixed
        "ndkLocation" to "com.android.build.gradle.tasks.ShaderCompile"
    )

    fun isIgnoredBeanField(field: Field) =
        ignoredBeanFields.contains(field.name to field.declaringClass.name)

    fun canReadSystemProperty(from: String): Boolean =
        withWorkaroundsFor("systemProps") {
            isBuildScanPlugin(from)
        }

    fun canReadEnvironmentVariable(from: String): Boolean =
        withWorkaroundsFor("envVars") {
            isBuildScanPlugin(from)
        }

    fun canStartExternalProcesses(from: String): Boolean =
        withWorkaroundsFor("processes") {
            isBuildScanPlugin(from) || isEnterpriseConventionsPlugin(from)
        }

    fun canReadFiles(from: String): Boolean =
        withWorkaroundsFor("files") {
            isBuildScanPlugin(from)
        }

    private
    fun isBuildScanPlugin(from: String): Boolean = from.run {
        startsWith("com.gradle.scan.plugin.internal.")
            || startsWith("com.gradle.enterprise.agent.")
    }

    // TODO(https://github.com/gradle/gradle-org-conventions-plugin/issues/18) Remove the workaround when our conventions plugin is compatible.
    private
    fun isEnterpriseConventionsPlugin(from: String): Boolean =
        from.startsWith("com.gradle.enterprise.conventions.")

    private
    inline fun withWorkaroundsFor(area: String, isEnabled: () -> Boolean): Boolean =
        isEnabled() && !shouldDisableInputWorkaroundsFor(area)

    private
    fun shouldDisableInputWorkaroundsFor(area: String): Boolean =
        System.getProperty("org.gradle.internal.disable.input.workarounds")?.contains(area, ignoreCase = true) ?: false
}
