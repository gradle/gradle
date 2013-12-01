/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classloader.MutableURLClassLoader;

import java.io.File;
import java.net.MalformedURLException;

public class DefaultScriptHandler extends AbstractScriptHandler {
    private final ScriptClassLoader classLoader;

    public DefaultScriptHandler(ScriptSource scriptSource, RepositoryHandler repositoryHandler,
                                DependencyHandler dependencyHandler, ConfigurationContainer configContainer,
                                ClassLoader baseClassLoader) {
        super(repositoryHandler, dependencyHandler, scriptSource, configContainer);
        this.classLoader = new ScriptClassLoader(baseClassLoader);
    }

    public ClassLoader getBaseCompilationClassLoader() {
        return classLoader.getParentLoader();
    }

    public ScriptClassLoader getClassLoader() {
        return classLoader;
    }

    public void addParent(ClassLoader parent) {
        classLoader.addParent(parent);
    }

    public void updateClassPath() {
        MutableURLClassLoader mutableClassLoader = classLoader.getMutableClassLoader();
        for (File file : getClasspathConfiguration().getFiles()) {
            try {
                mutableClassLoader.addURL(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
