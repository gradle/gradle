/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.initialization;

import org.gradle.api.initialization.dsl.ScriptClasspathHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.util.ConfigureUtil;
import groovy.lang.Closure;

public class DefaultScriptClasspathHandler implements ScriptClasspathHandler {
    private final RepositoryHandler repositoryHandler;
    private final DependencyHandler dependencyHandler;

    public DefaultScriptClasspathHandler(RepositoryHandler repositoryHandler, DependencyHandler dependencyHandler) {
        this.repositoryHandler = repositoryHandler;
        this.dependencyHandler = dependencyHandler;
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, dependencyHandler);
    }

    public DependencyHandler getDependencies() {
        return dependencyHandler;
    }

    public RepositoryHandler getRepositories() {
        return repositoryHandler;
    }

    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, repositoryHandler);
    }
}
