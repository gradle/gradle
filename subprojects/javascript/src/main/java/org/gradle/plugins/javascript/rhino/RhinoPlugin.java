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

package org.gradle.plugins.javascript.rhino;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.plugins.javascript.base.JavaScriptBasePlugin;
import org.gradle.plugins.javascript.base.JavaScriptExtension;

import java.util.concurrent.Callable;

public class RhinoPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaScriptBasePlugin.class);

        JavaScriptExtension jsExtension = project.getExtensions().findByType(JavaScriptExtension.class);
        final RhinoExtension rhinoExtension = ((ExtensionAware) jsExtension).getExtensions().create(RhinoExtension.NAME, RhinoExtension.class);

        final Configuration configuration = addClasspathConfiguration(project.getConfigurations());
        configureDefaultRhinoDependency(configuration, project.getDependencies(), rhinoExtension);

        ConventionMapping conventionMapping = ((IConventionAware) rhinoExtension).getConventionMapping();
        conventionMapping.map("classpath", new Callable<Configuration>() {
            @Override
            public Configuration call() {
                return configuration;
            }
        });
        conventionMapping.map("version", new Callable<String>() {
            @Override
            public String call() {
                return RhinoExtension.DEFAULT_RHINO_DEPENDENCY_VERSION;
            }
        });

        project.getTasks().withType(RhinoShellExec.class, new Action<RhinoShellExec>() {
            @Override
            public void execute(RhinoShellExec task) {
                task.getConventionMapping().map("classpath", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() {
                        return rhinoExtension.getClasspath();
                    }
                });
                task.getConventionMapping().map("main", new Callable<String>() {
                    @Override
                    public String call() {
                        return RhinoExtension.RHINO_SHELL_MAIN;
                    }
                });
                task.setClasspath(rhinoExtension.getClasspath());
            }
        });
    }

    private Configuration addClasspathConfiguration(ConfigurationContainer configurations) {
        Configuration configuration = configurations.create(RhinoExtension.CLASSPATH_CONFIGURATION_NAME);
        configuration.setVisible(false);
        configuration.setDescription("The default Rhino classpath");
        return configuration;
    }

    public void configureDefaultRhinoDependency(Configuration configuration, final DependencyHandler dependencyHandler, final RhinoExtension extension) {
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                Dependency dependency = dependencyHandler.create(RhinoExtension.DEFAULT_RHINO_DEPENDENCY_GROUP + ":" + RhinoExtension.DEFAULT_RHINO_DEPENDENCY_MODULE + ":" + extension.getVersion());
                dependencies.add(dependency);
            }
        });
    }
}
