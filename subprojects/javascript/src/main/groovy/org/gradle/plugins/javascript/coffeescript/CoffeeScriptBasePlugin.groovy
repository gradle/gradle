/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.plugins.javascript.coffeescript

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.plugins.javascript.base.JavaScriptExtension
import org.gradle.plugins.javascript.rhino.RhinoExtension

import static org.gradle.plugins.javascript.coffeescript.CoffeeScriptExtension.*

class CoffeeScriptBasePlugin implements Plugin<Project> {

    void apply(Project project) {
        project.apply(plugin: "rhino")

        JavaScriptExtension jsExtension = project.extensions.getByType(JavaScriptExtension)
        CoffeeScriptExtension csExtension = jsExtension.extensions.create(CoffeeScriptExtension.NAME, CoffeeScriptExtension)
        Configuration jsConfiguration = addJsConfiguration(project.configurations, project.dependencies, csExtension)

        csExtension.conventionMapping.with {
            map("js") { jsConfiguration }
            map("version") { DEFAULT_JS_DEPENDENCY_VERSION }
        }

        RhinoExtension rhinoExtension = jsExtension.extensions.getByType(RhinoExtension)

        project.tasks.withType(CoffeeScriptCompile) { CoffeeScriptCompile task ->
            task.conventionMapping.map("rhinoClasspath") { rhinoExtension.classpath }
            task.conventionMapping.map("coffeeScriptJs") { csExtension.js }
        }
    }

    private Configuration addJsConfiguration(ConfigurationContainer configurations, DependencyHandler dependencies, CoffeeScriptExtension extension) {
        Configuration configuration = configurations.create(CoffeeScriptExtension.JS_CONFIGURATION_NAME)
        configuration.incoming.beforeResolve(new Action<ResolvableDependencies>() {
            void execute(ResolvableDependencies resolvableDependencies) {
                if (configuration.dependencies.empty) {
                    String notation = "${DEFAULT_JS_DEPENDENCY_GROUP}:${DEFAULT_JS_DEPENDENCY_MODULE}:${extension.version}@js"
                    Dependency dependency = dependencies.create(notation)
                    configuration.dependencies.add(dependency)
                }
            }
        })
        configuration
    }
}
