/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl

import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


/**
 * The `gradle-enterprise` plugin.
 *
 * Visit the [Develocity Plugin User Manual](https://docs.gradle.com/enterprise/gradle-plugin/) for additional information.
 *
 * By default, the applied plugin version will be the same as the one used by the `--scan` command line option.
 *
 * You can also use e.g. `` `gradle-enterprise` version "3.0" `` to request a different version.
 *
 * @since 6.0
 */
@Deprecated("Gradle Enterprise has been renamed to Develocity", replaceWith = ReplaceWith("id(\"com.gradle.develocity\") version \"${AutoAppliedDevelocityPlugin.VERSION}\""))
val PluginDependenciesSpec.`gradle-enterprise`: PluginDependencySpec
    get() {
        DeprecationLogger.deprecate("The ${PluginDependencySpec::class.simpleName}.`gradle-enterprise` property")
            .withAdvice("Please use 'id(\"com.gradle.develocity\") version \"${AutoAppliedDevelocityPlugin.VERSION}\"' instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "gradle_enterprise_extension_deprecated")
            .nagUser()
        return this.id(AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ID.id).version(AutoAppliedDevelocityPlugin.VERSION)
    }
