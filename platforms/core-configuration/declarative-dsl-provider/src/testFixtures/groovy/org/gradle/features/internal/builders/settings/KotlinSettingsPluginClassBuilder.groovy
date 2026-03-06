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

package org.gradle.features.internal.builders.settings

import org.gradle.features.annotations.RegistersProjectFeatures
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A {@link SettingsPluginClassBuilder} that generates an ecosystem plugin class in Kotlin instead of Java.
 */
class KotlinSettingsPluginClassBuilder extends SettingsPluginClassBuilder {

    KotlinSettingsPluginClassBuilder() {
        this.pluginClassName = "ProjectFeatureRegistrationPlugin"
    }

    @Override
    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/kotlin/org/gradle/test/${pluginClassName}.kt") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import ${RegistersProjectFeatures.class.name}

            @${RegistersProjectFeatures.class.simpleName}(${(projectTypePluginClasses + projectFeaturePluginClasses).collect { it + "::class" }.join(", ")})
            class ${pluginClassName} : Plugin<Settings> {
                override fun apply(settings: Settings) {
                }
            }
        """
    }
}
