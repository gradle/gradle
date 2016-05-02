/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.initialization.ClassLoaderIds;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.DefaultClassPath;

public class TestClassLoaderFactory implements Factory<ClassLoader> {
    private final ClassLoaderCache classLoaderCache;
    private final Test testTask;
    private ClassLoader testClassLoader;

    public TestClassLoaderFactory(ClassLoaderCache classLoaderCache, Test testTask) {
        this.classLoaderCache = classLoaderCache;
        this.testTask = testTask;
    }

    @Override
    public ClassLoader create() {
        if (testClassLoader == null) {
            testClassLoader = classLoaderCache.get(ClassLoaderIds.testTaskClasspath(testTask.getPath()), new DefaultClassPath(testTask.getClasspath()), null, null);
        }
        return testClassLoader;
    }
}
