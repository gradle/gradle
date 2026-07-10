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

package org.gradle.features.internal.builders

/**
 * Generates a plain {@code Plugin<Project>} with an empty {@code apply()} method and no binding
 * annotation or inner classes. Used by {@code TestScenarioBuilder.plugin(String, Closure)} for
 * standalone plugins that participate in plugin-application flows without contributing a
 * project type or feature definition.
 */
class StandalonePluginBuilder extends AbstractPluginBuilder {

    StandalonePluginBuilder() {
        this.type = PluginType.WITHOUT_BINDINGS
    }

    @Override
    protected String renderJava() {
        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            abstract public class ${pluginClassName} implements Plugin<Project> {
                @Override
                public void apply(Project target) {

                }
            }
        """
    }

    @Override
    protected String renderKotlin() {
        return """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            abstract class ${pluginClassName} : Plugin<Project> {
                override fun apply(target: Project) {
                }
            }
        """
    }
}
