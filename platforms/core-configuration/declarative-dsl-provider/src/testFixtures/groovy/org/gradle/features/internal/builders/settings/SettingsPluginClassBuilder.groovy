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
 * Builder for generating a settings plugin class that registers project type and project feature plugins.
 *
 * This class is used as a basis for subclasses that override methods to create settings plugins with different behavior and qualities.
 */
class SettingsPluginClassBuilder {
    String pluginClassName = "ProjectTypeRegistrationPlugin"
    List<String> projectTypePluginClasses = []
    List<String> projectFeaturePluginClasses = []

    SettingsPluginClassBuilder registersProjectType(String projectTypePluginClass) {
        this.projectTypePluginClasses.add(projectTypePluginClass)
        return this
    }

    SettingsPluginClassBuilder registersProjectFeature(String projectFeaturePluginClass) {
        this.projectFeaturePluginClasses.add(projectFeaturePluginClass)
        return this
    }

    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/java/org/gradle/test/${pluginClassName}.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import ${RegistersProjectFeatures.class.name};

            @${RegistersProjectFeatures.class.simpleName}({ ${(projectTypePluginClasses + projectFeaturePluginClasses).collect { it + ".class" }.join(", ")} })
            abstract public class ${pluginClassName} implements Plugin<Settings> {
                @Override
                public void apply(Settings target) { }
            }
        """
    }
}
