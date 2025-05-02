/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.classloader.FilteringClassLoader;

public class FlatClassLoaderRegistry implements ClassLoaderRegistry {

    private final ClassLoader classLoader;

    public FlatClassLoaderRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public ClassLoader getGradleApiClassLoader() {
        return classLoader;
    }

    @Override
    public ClassLoader getRuntimeClassLoader() {
        return classLoader;
    }

    @Override
    public ClassLoader getPluginsClassLoader() {
        return classLoader;
    }

    @Override
    public ClassLoader getGradleCoreApiClassLoader() {
        return classLoader;
    }

    @Override
    public FilteringClassLoader.Spec getGradleApiFilterSpec() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MixInLegacyTypesClassLoader.Spec getGradleWorkerExtensionSpec() {
        throw new UnsupportedOperationException();
    }
}
