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

package org.gradle.plugins.javascript.rhino

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.plugins.javascript.base.JavaScriptExtension

import static org.gradle.plugins.javascript.rhino.RhinoExtension.*

class RhinoPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.apply(plugin: "javascript-base")

        JavaScriptExtension jsExtension = project.extensions.findByType(JavaScriptExtension)
        RhinoExtension rhinoExtension = jsExtension.extensions.create(RhinoExtension.NAME, RhinoExtension)

        def configuration = addClasspathConfiguration(project.configurations)
        configureDefaultRhinoDependency(configuration, project.dependencies, rhinoExtension)

        rhinoExtension.conventionMapping.with {
            classpath = { configuration }
            version = { DEFAULT_RHINO_DEPENDENCY_VERSION }
        }

        project.tasks.withType(RhinoShellExec) { RhinoShellExec task ->
            task.conventionMapping.with {
                classpath = { rhinoExtension.classpath }
                main = { RhinoExtension.RHINO_SHELL_MAIN }
            }
            task.classpath = rhinoExtension.classpath

        }
    }

    private Configuration addClasspathConfiguration(ConfigurationContainer configurations) {
        def configuration = configurations.create(CLASSPATH_CONFIGURATION_NAME)
        configuration.visible = false
        configuration.description = "The default Rhino classpath"
        configuration
    }

    void configureDefaultRhinoDependency(Configuration configuration, DependencyHandler dependencyHandler, RhinoExtension extension) {
        configuration.incoming.beforeResolve {
            if (configuration.dependencies.empty) {
                Dependency dependency = dependencyHandler.create("${DEFAULT_RHINO_DEPENDENCY_GROUP}:${DEFAULT_RHINO_DEPENDENCY_MODULE}:${extension.version}")
                configuration.dependencies.add(dependency)
            }
        }
    }

}
