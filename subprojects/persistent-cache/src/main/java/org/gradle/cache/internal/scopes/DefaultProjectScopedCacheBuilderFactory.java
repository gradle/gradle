/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal.scopes;

import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.scopes.ProjectScopedCacheBuilderFactory;

import java.io.File;

/**
 * Default implementation of {@link ProjectScopedCacheBuilderFactory}, implements interface using {@link AbstractScopedCacheBuilderFactory}.
 */
public class DefaultProjectScopedCacheBuilderFactory extends AbstractScopedCacheBuilderFactory implements ProjectScopedCacheBuilderFactory {
    public DefaultProjectScopedCacheBuilderFactory(File rootDir, UnscopedCacheBuilderFactory unscopedCacheBuilderFactory) {
        super(rootDir, unscopedCacheBuilderFactory);
    }
}
