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
package org.gradle.internal.classloader;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

/**
 * A {@link ClassLoaderFactory} that also stores the hash of each created classloader which is later retrievable via {@link #getClassLoaderClasspathHash(ClassLoader)}.
 */
public interface HashingClassLoaderFactory extends ClassLoaderFactory {
    /**
     * Creates a {@link ClassLoader} with the given parent and classpath. Use the given hash
     * code, or calculate it from the given classpath when hash code is {@code null}.
     */
    ClassLoader createChildClassLoader(String name, ClassLoader parent, ClassPath classPath, @Nullable HashCode implementationHash);

    /**
     * Returns the hash associated with the classloader's classpath, or {@link null} if the classloader is unknown to Gradle.
     * The hash only represents the classloader's classpath only, regardless of whether or not there are any parent classloaders.
     */
    @Nullable
    HashCode getClassLoaderClasspathHash(ClassLoader classLoader);
}
