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

import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

/**
 * Represents a transformer that takes a given ClassPath and transforms it to a ClassPath with cached jars
 */
@ServiceScope(Scopes.UserHome.class)
public interface CachedClasspathTransformer {
    enum StandardTransform {
        BuildLogic, None
    }

    /**
     * Transforms a classpath to a classpath with the given transformations applied.
     */
    ClassPath transform(ClassPath classPath, StandardTransform transform);

    /**
     * Transforms a classpath to a classpath with the given transformations applied.
     */
    ClassPath transform(ClassPath classPath, StandardTransform transform, Transform additional);

    /**
     * Transform a collection of urls to a new collection where the file urls are cached jars
     */
    Collection<URL> transform(Collection<URL> urls, StandardTransform transform);

    interface Transform {
        void applyConfigurationTo(Hasher hasher);

        Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor) throws IOException;
    }
}
