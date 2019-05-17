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

package org.gradle.plugins.javascript.envjs;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.internal.Factory;
import org.gradle.plugins.javascript.base.JavaScriptExtension;
import org.gradle.plugins.javascript.envjs.browser.BrowserEvaluate;
import org.gradle.plugins.javascript.envjs.internal.EnvJsBrowserEvaluator;
import org.gradle.plugins.javascript.rhino.RhinoExtension;
import org.gradle.plugins.javascript.rhino.RhinoPlugin;
import org.gradle.plugins.javascript.rhino.worker.internal.DefaultRhinoWorkerHandleFactory;
import org.gradle.plugins.javascript.rhino.worker.internal.RhinoWorkerHandleFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

public class EnvJsPlugin implements Plugin<Project> {
    private final WorkerProcessFactory workerProcessBuilderFactory;

    @Inject
    public EnvJsPlugin(WorkerProcessFactory workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(RhinoPlugin.class);
        project.getPluginManager().apply(ReportingBasePlugin.class);

        JavaScriptExtension jsExtension = project.getExtensions().getByType(JavaScriptExtension.class);
        final EnvJsExtension envJsExtension = ((ExtensionAware) jsExtension).getExtensions().create(EnvJsExtension.NAME, EnvJsExtension.class);

        final Configuration configuration = addConfiguration(project.getConfigurations(), project.getDependencies(), envJsExtension);
        final ConventionMapping conventionMapping = ((IConventionAware) envJsExtension).getConventionMapping();
        conventionMapping.map("js", new Callable<Configuration>() {
            @Override
            public Configuration call() {
                return configuration;
            }

        });
        conventionMapping.map("version", new Callable<String>() {
            @Override
            public String call() {
                return EnvJsExtension.DEFAULT_DEPENDENCY_VERSION;
            }
        });

        final RhinoExtension rhinoExtension = ((ExtensionAware) jsExtension).getExtensions().getByType(RhinoExtension.class);

        project.getTasks().withType(BrowserEvaluate.class, new Action<BrowserEvaluate>() {
            @Override
            public void execute(BrowserEvaluate task) {
                ((IConventionAware) task).getConventionMapping().map("evaluator", new Callable<EnvJsBrowserEvaluator>() {
                    @Override
                    public EnvJsBrowserEvaluator call() {
                        RhinoWorkerHandleFactory handleFactory = new DefaultRhinoWorkerHandleFactory(workerProcessBuilderFactory);
                        File workDir = project.getProjectDir();
                        Factory<File> envJsFactory = new Factory<File>() {
                            @Override
                            public File create() {
                                return envJsExtension.getJs().getSingleFile();
                            }
                        };
                        return new EnvJsBrowserEvaluator(handleFactory, rhinoExtension.getClasspath(), envJsFactory, project.getGradle().getStartParameter().getLogLevel(), workDir);
                    }
                });
            }
        });
    }

    public Configuration addConfiguration(ConfigurationContainer configurations, final DependencyHandler dependencies, final EnvJsExtension extension) {
        Configuration configuration = configurations.create(EnvJsExtension.CONFIGURATION_NAME);
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet configDependencies) {
                String notation = EnvJsExtension.DEFAULT_DEPENDENCY_GROUP + ":" + EnvJsExtension.DEFAULT_DEPENDENCY_MODULE + ":" + extension.getVersion() + "@js";
                Dependency dependency = dependencies.create(notation);
                configDependencies.add(dependency);
            }

        });
        return configuration;
    }
}
