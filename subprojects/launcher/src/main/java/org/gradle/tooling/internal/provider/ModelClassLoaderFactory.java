/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.TaskExecutionRequest;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;

import java.util.List;

public class ModelClassLoaderFactory implements PayloadClassLoaderFactory {
    private final ClassLoader rootClassLoader;
    private final ClassLoaderFactory classLoaderFactory;

    public ModelClassLoaderFactory(ClassLoaderFactory classLoaderFactory) {
        this.classLoaderFactory = classLoaderFactory;
        ClassLoader parent = getClass().getClassLoader();
        FilteringClassLoader filter = new FilteringClassLoader(parent);
        filter.allowPackage("org.gradle.tooling.internal.protocol");
        filter.allowClass(TaskExecutionRequest.class);
        rootClassLoader = filter;
    }

    public ClassLoader getClassLoaderFor(ClassLoaderSpec spec, List<? extends ClassLoader> parents) {
        if (spec.equals(ClassLoaderSpec.SYSTEM_CLASS_LOADER)) {
            return rootClassLoader;
        }
        return classLoaderFactory.createClassLoader(spec, parents);
    }
}
