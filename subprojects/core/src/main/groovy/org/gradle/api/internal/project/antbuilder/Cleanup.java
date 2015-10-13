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

import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.internal.classpath.ClassPath;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

public class Cleanup extends PhantomReference<CachedClassLoader> {
    private final ClassPath key;
    private final ClassLoader classLoader;
    private final GroovySystemLoader groovySystemForClassLoader;
    private final GroovySystemLoader gradleApiGroovyLoader;
    private final GroovySystemLoader antBuilderGroovyLoader;

    public Cleanup(ClassPath classPath,
                   CachedClassLoader cachedClassLoader,
                   ReferenceQueue<CachedClassLoader> referenceQueue,
                   ClassLoader classLoader,
                   GroovySystemLoader groovySystemForClassLoader,
                   GroovySystemLoader gradleApiGroovyLoader,
                   GroovySystemLoader antBuilderGroovyLoader) {
        super(cachedClassLoader, referenceQueue);
        this.groovySystemForClassLoader = groovySystemForClassLoader;
        this.gradleApiGroovyLoader = gradleApiGroovyLoader;
        this.antBuilderGroovyLoader = antBuilderGroovyLoader;
        this.key = classPath;
        this.classLoader = classLoader;
    }

    public ClassPath getKey() {
        return key;
    }

    public void cleanup() {
        groovySystemForClassLoader.shutdown();
        gradleApiGroovyLoader.discardTypesFrom(classLoader);
        antBuilderGroovyLoader.discardTypesFrom(classLoader);
    }
}
