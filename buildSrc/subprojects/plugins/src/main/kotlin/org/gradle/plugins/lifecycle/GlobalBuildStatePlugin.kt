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

package org.gradle.plugins.lifecycle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*


/**
 * This sets a global state as project properties for certain lifecycle tasks.
 * This plugin should be applied in the beginning of the root build script.
 *
 * Ideally, the build should be improved to avoid these states and make this plugin,
 * which depends on application order, obsolete.
 */
class GlobalBuildStatePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        if (needsToIgnoreIncomingBuildReceipt()) {
            globalProperty("ignoreIncomingBuildReceipt" to true)
        }
        if (needsToUseTestVersionsPartial()) {
            globalProperty("testVersions" to "partial")
        }
        if (needsToUseTestVersionsAll()) {
            globalProperty("testVersions" to "all")
        }
        if (needsToUseAllDistribution()) {
            globalProperty("useAllDistribution" to true)
        }
    }

    private
    fun Project.needsToIgnoreIncomingBuildReceipt() = isRequestedTask("compileAllBuild")

    private
    fun Project.needsToUseTestVersionsPartial() = isRequestedTask("platformTest")

    private
    fun Project.needsToUseTestVersionsAll() = isRequestedTask("allVersionsCrossVersionTest")
        || isRequestedTask("allVersionsIntegMultiVersionTest")
        || isRequestedTask("soakTest")

    private
    fun Project.needsToUseAllDistribution() = isRequestedTask("quickFeedbackCrossVersionTest")
        || isRequestedTask("allVersionsCrossVersionTest")
        || isRequestedTask("allVersionsIntegMultiVersionTest")
        || isRequestedTask("noDaemonTest")

    private
    fun Project.globalProperty(pair: Pair<String, Any>) {
        val propertyName = pair.first
        val value = pair.second
        if (hasProperty(propertyName)) {
            val otherValue = property(propertyName)
            if (value.toString() != otherValue.toString()) {
                throw RuntimeException("Attempting to set global property $propertyName to two different values ($value vs $otherValue)")
            }
        }
        extra.set(propertyName, value)
    }

    private
    fun Project.isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
}
