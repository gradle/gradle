/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.classpath.ClassPath;

import java.util.ArrayList;
import java.util.List;


public class RecordingClassLoaderScopeRegistry implements ClassLoaderScopeRegistry {

    private final RecordingClassLoaderScope coreScope;
    private final RecordingClassLoaderScope coreAndPluginsScope;

    private final RecordedClassLoaderScopeSpec coreHierarchySpec = new RecordedClassLoaderScopeSpec("root");
    private final RecordedClassLoaderScopeSpec coreAndPluginsHierarchySpec = new RecordedClassLoaderScopeSpec("root");

    public RecordingClassLoaderScopeRegistry(ClassLoaderScopeRegistry delegate) {
        this.coreScope = new RecordingClassLoaderScope(delegate.getCoreScope(), coreHierarchySpec);
        this.coreAndPluginsScope = new RecordingClassLoaderScope(delegate.getCoreAndPluginsScope(), coreAndPluginsHierarchySpec);
    }

    public List<RecordedClassLoaderScopeSpec> getCoreChildrenSpec() {
        return coreHierarchySpec.children;
    }

    public List<RecordedClassLoaderScopeSpec> getCoreAndPluginsChildrenSpec() {
        return coreAndPluginsHierarchySpec.children;
    }

    @Override
    public ClassLoaderScope getCoreScope() {
        return coreScope;
    }

    @Override
    public ClassLoaderScope getCoreAndPluginsScope() {
        return coreAndPluginsScope;
    }

    private class RecordingClassLoaderScope implements ClassLoaderScope {

        private final ClassLoaderScope delegate;
        private final RecordedClassLoaderScopeSpec spec;

        private RecordingClassLoaderScope(ClassLoaderScope delegate, RecordedClassLoaderScopeSpec spec) {
            this.delegate = delegate;
            this.spec = spec;
        }

        @Override
        public ClassLoader getLocalClassLoader() {
            return delegate.getLocalClassLoader();
        }

        @Override
        public ClassLoader getExportClassLoader() {
            return delegate.getExportClassLoader();
        }

        @Override
        public ClassLoaderScope getParent() {
            return delegate.getParent();
        }

        @Override
        public boolean defines(Class<?> clazz) {
            return delegate.defines(clazz);
        }

        @Override
        public ClassLoaderScope local(ClassPath classPath) {
            delegate.local(classPath);
            spec.localClassPath = spec.localClassPath.plus(classPath);
            return this;
        }

        @Override
        public ClassLoaderScope export(ClassPath classPath) {
            delegate.export(classPath);
            spec.exportClassPath = spec.exportClassPath.plus(classPath);
            return this;
        }

        @Override
        public ClassLoaderScope export(ClassLoader classLoader) {
            // TODO:instant-execution See pluginsFromOtherLoaders in DefaultPluginRequestApplicator and InjectedClasspathPluginResolution
            // Ignore this for instant-execution, it's only used for the dodgy TestKit injected classpath
            delegate.export(classLoader);
            return this;
        }

        @Override
        public ClassLoaderScope createChild(String id) {
            ClassLoaderScope child = delegate.createChild(id);
            RecordedClassLoaderScopeSpec childSpec = new RecordedClassLoaderScopeSpec(id);
            spec.children.add(childSpec);
            return new RecordingClassLoaderScope(child, childSpec);
        }

        @Override
        public ClassLoaderScope lock() {
            delegate.lock();
            return this;
        }

        @Override
        public boolean isLocked() {
            return delegate.isLocked();
        }
    }

    public static class RecordedClassLoaderScopeSpec {

        private final String id;
        private ClassPath localClassPath = ClassPath.EMPTY;
        private ClassPath exportClassPath = ClassPath.EMPTY;
        private final List<RecordedClassLoaderScopeSpec> children = new ArrayList<>();

        private RecordedClassLoaderScopeSpec(String id) {
            this.id = id;
        }

        public RecordedClassLoaderScopeSpec(String id, ClassPath localClassPath, ClassPath exportClassPath, List<RecordedClassLoaderScopeSpec> children) {
            this(id);
            this.localClassPath = localClassPath;
            this.exportClassPath = exportClassPath;
            this.children.addAll(children);
        }

        public String getId() {
            return id;
        }

        public ClassPath getLocalClassPath() {
            return localClassPath;
        }

        public ClassPath getExportClassPath() {
            return exportClassPath;
        }

        public List<RecordedClassLoaderScopeSpec> getChildren() {
            return children;
        }
    }
}
