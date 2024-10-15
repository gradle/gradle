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

package org.gradle.internal.classpath;

import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.net.URL;
import java.util.Collection;

/**
 * Represents a transformer that takes a given ClassPath and transforms it to a ClassPath with cached jars
 */
@ServiceScope(Scope.UserHome.class)
public interface CachedClasspathTransformer {

    /**
     * Transforms a classpath to a new classpath with a no-op transform that copies files to a cache location.
     */
    ClassPath copyingTransform(ClassPath classPath);

    /**
     * Transform a collection of urls to a new collection with a no-op transform that copies files to a cache location.
     */
    Collection<URL> copyingTransform(Collection<URL> urls);

    /**
     * Transforms a classpath to a classpath with the given transformations applied, result classpath files are cached in a cache location.
     */
    ClassPath transform(ClassPath classPath, ClassTransform classTransform);
}
