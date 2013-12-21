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
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultScriptHandler extends AbstractScriptHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScriptHandler.class);
    private final ScriptCompileScope parentScope;
    private List<ClassLoader> parents = new ArrayList<ClassLoader>();
    private ClassLoader classLoader;
    private MutableURLClassLoader scriptClassPathClassLoader;
    private MultiParentClassLoader multiParentClassLoader;

    public DefaultScriptHandler(ScriptSource scriptSource, RepositoryHandler repositoryHandler,
                                DependencyHandler dependencyHandler, ConfigurationContainer configContainer,
                                ScriptCompileScope parentScope) {
        super(repositoryHandler, dependencyHandler, scriptSource, configContainer);
        this.parentScope = parentScope;
    }

    public ClassLoader getBaseCompilationClassLoader() {
        return parentScope.getScriptCompileClassLoader();
    }

    public ClassLoader getScriptCompileClassLoader() {
        if (classLoader == null) {
            // This is for backwards compatibility - it is possible to query the script ClassLoader before it has been finalized.
            // So, eagerly create the most flexible ClassLoader structure in case it is required.
            LOGGER.debug("Eager creation of script class loader for {}. This may result in performance issues.", getSourceFile());
            scriptClassPathClassLoader = new MutableURLClassLoader(getBaseCompilationClassLoader());
            multiParentClassLoader = new MultiParentClassLoader(scriptClassPathClassLoader);
            classLoader = new CachingClassLoader(multiParentClassLoader);
        }
        return classLoader;
    }

    public void addParent(ClassLoader parent) {
        if (parents == null) {
            throw new IllegalStateException("Cannot add a parent ClassLoader after script ClassLoader has been finalized.");
        }
        parents.add(parent);
    }

    public void updateClassPath() {
        if (classLoader == null) {
            ClassLoader current = getBaseCompilationClassLoader();
            Set<File> classPath = getClasspathConfiguration().getFiles();
            if (!classPath.isEmpty()) {
                MutableURLClassLoader mutableClassLoader = new MutableURLClassLoader(current);
                addClassPath(mutableClassLoader);
                current = mutableClassLoader;
            }
            if (!parents.isEmpty()) {
                parents.add(0, current);
                current = new CachingClassLoader(new MultiParentClassLoader(parents));
            }
            this.classLoader = current;
        } else {
            // This is for backwards compatibility - see the comment in #getClassLoader() above
            // Here, we fill in the missing bits that weren't available when the ClassLoader was eagerly created
            addClassPath(scriptClassPathClassLoader);
            for (ClassLoader parent : parents) {
                multiParentClassLoader.addParent(parent);
            }
        }
        parents = null;
    }

    private void addClassPath(MutableURLClassLoader mutableClassLoader) {
        for (File file : getClasspathConfiguration().getFiles()) {
            try {
                mutableClassLoader.addURL(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
