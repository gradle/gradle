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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.util.ConfigureUtil;

public class RhinoExtension {

    public static final String NAME = "rhino";
    public static final String RHINO_SHELL_MAIN = "org.mozilla.javascript.tools.shell.Main";

    private final FileResolver fileResolver;
    private final DependencyHandler dependencyHandler;
    private final Configuration configuration;
    private final Dependency defaultDependency;

    public RhinoExtension(FileResolver fileResolver, DependencyHandler dependencyHandler, Configuration configuration, Dependency defaultDependency) {
        this.fileResolver = fileResolver;
        this.dependencyHandler = dependencyHandler;
        this.configuration = configuration;
        this.defaultDependency = defaultDependency;

        configureConfiguration(configuration);
    }

    public void dependencies(Object notation) {
        dependencies(notation, null);
    }

    public void dependencies(Object notation, Closure closure) {
        configuration.getDependencies().add(dependencyHandler.create(notation, closure));
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    private void configureConfiguration(final Configuration configuration) {
        configuration.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            public void execute(ResolvableDependencies resolvableDependencies) {
                if (resolvableDependencies.getDependencies().isEmpty()) {
                    configuration.getDependencies().add(defaultDependency);
                }
            }
        });
    }

    public JavaExecAction exec() {
        JavaExecAction action = new DefaultJavaExecAction(fileResolver);
        action.setMain(RHINO_SHELL_MAIN);
        action.setClasspath(getConfiguration());
        return action;
    }

    public ExecResult exec(Action<JavaExecSpec> action) {
        JavaExecAction exec = exec();
        action.execute(exec);
        return exec.execute();
    }

    public ExecResult exec(Closure<?> action) {
        return ConfigureUtil.configure(action, exec()).execute();
    }

}
