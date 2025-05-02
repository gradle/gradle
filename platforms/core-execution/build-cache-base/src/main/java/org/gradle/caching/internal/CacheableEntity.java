/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.caching.internal;

import org.gradle.internal.file.TreeType;

import java.io.File;

/**
 * An entity that can potentially be stored in the build cache.
 */
public interface CacheableEntity {
    /**
     * The identity of the work as a part of the build to be reported in the origin metadata.
     */
    String getIdentity();

    /**
     * The type of the work to report in the origin metadata.
     */
    Class<?> getType();

    String getDisplayName();

    void visitOutputTrees(CacheableTreeVisitor visitor);

    @FunctionalInterface
    interface CacheableTreeVisitor {
        void visitOutputTree(String name, TreeType type, File root);
    }
}
