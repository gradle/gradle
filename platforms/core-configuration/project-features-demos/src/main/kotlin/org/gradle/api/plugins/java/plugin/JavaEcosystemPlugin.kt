/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.java.plugin

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.java.JavaProjectType
import org.gradle.api.plugins.testing.JvmTestSuiteFeature
import org.gradle.api.plugins.testing.KotlinTestJvmTestSuiteFeature
import org.gradle.features.annotations.RegistersProjectFeatures

@RegistersProjectFeatures(
    JavaProjectTypePlugin::class,

    JvmTestSuiteFeature::class,
    KotlinTestJvmTestSuiteFeature::class,
)
class JavaEcosystemPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        target.defaults.add("javaLibrary", JavaProjectType::class.java) { definition ->
            definition.sources.register("main")
            definition.sources.register("test")
        }
    }
}
