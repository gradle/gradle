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

package org.gradle.plugins.javascript.coffeescript;

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
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.plugins.javascript.base.JavaScriptExtension;
import org.gradle.plugins.javascript.rhino.RhinoExtension;
import org.gradle.plugins.javascript.rhino.RhinoPlugin;

import java.util.concurrent.Callable;

public class CoffeeScriptBasePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(RhinoPlugin.class);

        JavaScriptExtension jsExtension = project.getExtensions().getByType(JavaScriptExtension.class);

        ExtensionContainer extensionContainer = ((ExtensionAware) jsExtension).getExtensions();
        final CoffeeScriptExtension csExtension = extensionContainer.create(CoffeeScriptExtension.NAME, CoffeeScriptExtension.class);
        final Configuration jsConfiguration = addJsConfiguration(project.getConfigurations(), project.getDependencies(), csExtension);

        ConventionMapping conventionMapping = ((IConventionAware) csExtension).getConventionMapping();
        conventionMapping.map("js", new Callable<Configuration>() {
            @Override
            public Configuration call() {
                return jsConfiguration;
            }
        });
        conventionMapping.map("version", new Callable<String>() {
            @Override
            public String call() {
                return CoffeeScriptExtension.DEFAULT_JS_DEPENDENCY_VERSION;
            }
        });

        final RhinoExtension rhinoExtension = extensionContainer.getByType(RhinoExtension.class);

        project.getTasks().withType(CoffeeScriptCompile.class, new Action<CoffeeScriptCompile>() {
            @Override
            public void execute(CoffeeScriptCompile task) {
                task.getConventionMapping().map("rhinoClasspath", new Callable<FileCollection>() {
                    public FileCollection call() {
                        return rhinoExtension.getClasspath();
                    }
                });
                task.getConventionMapping().map("coffeeScriptJs", new Callable<FileCollection>() {
                    public FileCollection call() {
                        return csExtension.getJs();
                    }
                });
            }
        });
    }

    private Configuration addJsConfiguration(ConfigurationContainer configurations, final DependencyHandler dependencies, final CoffeeScriptExtension extension) {
        Configuration configuration = configurations.create(CoffeeScriptExtension.JS_CONFIGURATION_NAME);
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet configDependencies) {
                String notation = CoffeeScriptExtension.DEFAULT_JS_DEPENDENCY_GROUP + ":" + CoffeeScriptExtension.DEFAULT_JS_DEPENDENCY_MODULE + ":" + extension.getVersion() + "@js";
                Dependency dependency = dependencies.create(notation);
                configDependencies.add(dependency);
            }
        });
        return configuration;
    }
}
