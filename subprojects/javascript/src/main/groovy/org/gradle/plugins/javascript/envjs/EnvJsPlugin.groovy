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

package org.gradle.plugins.javascript.envjs

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.internal.Factory
import org.gradle.plugins.javascript.base.JavaScriptExtension
import org.gradle.plugins.javascript.envjs.browser.BrowserEvaluate
import org.gradle.plugins.javascript.envjs.internal.EnvJsBrowserEvaluator
import org.gradle.plugins.javascript.rhino.RhinoExtension
import org.gradle.plugins.javascript.rhino.RhinoPlugin
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerHandleFactory
import org.gradle.plugins.javascript.rhino.worker.internal.DefaultRhinoWorkerHandleFactory
import org.gradle.process.internal.WorkerProcessBuilder

import javax.inject.Inject

import static org.gradle.plugins.javascript.envjs.EnvJsExtension.*

class EnvJsPlugin implements Plugin<Project> {
    private final Factory<WorkerProcessBuilder> workerProcessBuilderFactory

    @Inject
    EnvJsPlugin(Factory<WorkerProcessBuilder> workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory
    }

    void apply(Project project) {
        project.plugins.apply(RhinoPlugin)
        project.plugins.apply(ReportingBasePlugin)

        JavaScriptExtension jsExtension = project.extensions.getByType(JavaScriptExtension)
        EnvJsExtension envJsExtension = jsExtension.extensions.create(EnvJsExtension.NAME, EnvJsExtension)

        Configuration configuration = addConfiguration(project.configurations, project.dependencies, envJsExtension)

        envJsExtension.conventionMapping.with {
            map("js") { configuration }
            map("version") { DEFAULT_DEPENDENCY_VERSION }
        }

        RhinoExtension rhinoExtension = jsExtension.extensions.getByType(RhinoExtension)

        project.tasks.withType(BrowserEvaluate) { BrowserEvaluate task ->
            conventionMapping.with {
                map("evaluator") {
                    RhinoWorkerHandleFactory handleFactory = new DefaultRhinoWorkerHandleFactory(workerProcessBuilderFactory);

                    File workDir = project.projectDir
                    Factory<File> envJsFactory = new Factory<File>() {
                        File create() {
                            envJsExtension.js.singleFile
                        }
                    }

                    new EnvJsBrowserEvaluator(handleFactory, rhinoExtension.classpath, envJsFactory, project.gradle.startParameter.logLevel, workDir)
                }
            }
        }

    }

    Configuration addConfiguration(ConfigurationContainer configurations, DependencyHandler dependencies, EnvJsExtension extension) {
        Configuration configuration = configurations.create(EnvJsExtension.CONFIGURATION_NAME)
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
