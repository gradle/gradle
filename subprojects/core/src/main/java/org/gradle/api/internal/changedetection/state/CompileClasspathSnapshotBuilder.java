/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.StringInterner;

/**
 * Builds a {@link FileCollectionSnapshot} for a compile classpath.
 *
 * We only take class files in jar files and class files in directories into account.
 */
public class CompileClasspathSnapshotBuilder extends AbstractClasspathSnapshotBuilder {
    public CompileClasspathSnapshotBuilder(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        super(classpathResourceHasher, cacheService, stringInterner);
    }

    @Override
    protected void visitNonJar(RegularFileSnapshot file) {
    }
}
