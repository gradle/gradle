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
 * An ecosystem plugin builder that configures a project type's defaults.
 */
class SettingsPluginThatConfiguresProjectTypeDefaultsBuilder extends SettingsPluginClassBuilder {
    private String definitionImplementationTypeClassName = "TestProjectTypeDefinition"

    SettingsPluginThatConfiguresProjectTypeDefaultsBuilder definitionImplementationTypeClassName(String definitionImplementationTypeClassName) {
        this.definitionImplementationTypeClassName = definitionImplementationTypeClassName
        return this
    }

    @Override
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
                public void apply(Settings settings) {
                    settings.getDefaults().add("testProjectType", ${definitionImplementationTypeClassName}.class, definition -> {
                        definition.getId().convention("settings");
                        definition.getFoo().getBar().convention("settings");
                    });
                }
            }
        """
    }
}
