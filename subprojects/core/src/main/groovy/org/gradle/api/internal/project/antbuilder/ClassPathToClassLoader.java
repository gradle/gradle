/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import org.gradle.internal.classpath.ClassPath;

public class ClassPathToClassLoader {
    private final static String ISOLATED_ANT_CLASS_LOADER = "Isolated Ant Classpath";

    private ClassLoader classLoader;
    private MemoryLeakPrevention classLoaderLeakPrevention;
    private final MemoryLeakPrevention gradleToIsolatedLeakPrevention;
    private final MemoryLeakPrevention antToIsolatedLeakPrevention;

    public ClassPathToClassLoader(ClassPath classPath, ClassLoader classLoader,
                                  MemoryLeakPrevention gradleToIsolatedLeakPrevention,
                                  MemoryLeakPrevention antToIsolatedLeakPrevention) {
        this.classLoader = classLoader;
        this.gradleToIsolatedLeakPrevention = gradleToIsolatedLeakPrevention;
        this.antToIsolatedLeakPrevention = antToIsolatedLeakPrevention;
        this.classLoaderLeakPrevention = new MemoryLeakPrevention(ISOLATED_ANT_CLASS_LOADER, classLoader, classPath);
        this.classLoaderLeakPrevention.prepare();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public MemoryLeakPrevention getClassLoaderLeakPrevention() {
        return classLoaderLeakPrevention;
    }

    public void cleanup() {
        // clean classes from the isolated builder which leak into the various loaders
        classLoaderLeakPrevention.dispose(classLoader, antToIsolatedLeakPrevention.getLeakingLoader(),  this.getClass().getClassLoader());

        // clean classes from the Gradle Core loader which leaked into the isolated builder and Ant loader
        gradleToIsolatedLeakPrevention.dispose(classLoader);

        // clean classes from the Gradle "ant" loader which leaked into the isolated builder
        antToIsolatedLeakPrevention.dispose(classLoader);


        classLoader = null;
        classLoaderLeakPrevention = null;
    }


}
