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

package org.gradle.plugins.javascript.jshint

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
import org.gradle.plugins.javascript.rhino.RhinoPlugin

import static org.gradle.plugins.javascript.jshint.JsHintExtension.*
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.ReportingExtension

class JsHintPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply(RhinoPlugin)
        project.plugins.apply(ReportingBasePlugin)

        JavaScriptExtension jsExtension = project.extensions.getByType(JavaScriptExtension)
        JsHintExtension jsHintExtension = jsExtension.extensions.create(JsHintExtension.NAME, JsHintExtension)
        Configuration configuration = addConfiguration(project.configurations, project.dependencies, jsHintExtension)

        jsHintExtension.conventionMapping.with {
            map("js") { configuration }
            map("version") { DEFAULT_DEPENDENCY_VERSION }
        }

        def rhinoExtension = jsExtension.extensions.getByType(RhinoExtension)
        def reportingExtension = project.extensions.getByType(ReportingExtension)

        project.tasks.withType(JsHint) { JsHint task ->
            task.conventionMapping.map("rhinoClasspath") { rhinoExtension.classpath }
            task.conventionMapping.map("jsHint") { jsHintExtension.js }
            task.conventionMapping.map("jsonReport") { reportingExtension.file("${task.getName()}/report.json") }
        }
    }

    Configuration addConfiguration(ConfigurationContainer configurations, DependencyHandler dependencies, JsHintExtension extension) {
        Configuration configuration = configurations.create(JsHintExtension.CONFIGURATION_NAME)
        configuration.incoming.beforeResolve(new Action<ResolvableDependencies>() {
            void execute(ResolvableDependencies resolvableDependencies) {
                if (configuration.dependencies.empty) {
                    String notation = "${DEFAULT_DEPENDENCY_GROUP}:${DEFAULT_DEPENDENCY_MODULE}:${extension.version}@js"
                    Dependency dependency = dependencies.create(notation)
                    configuration.dependencies.add(dependency)
                }
            }
        })
        configuration

    }
}
