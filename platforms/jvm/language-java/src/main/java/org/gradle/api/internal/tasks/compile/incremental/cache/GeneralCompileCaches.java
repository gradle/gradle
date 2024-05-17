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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.cache.Cache;
import org.gradle.internal.hash.HashCode;

/**
 * The build scoped compile caches.
 *
 * NOTE: This class cannot be renamed because it used to leak onto the public API
 * and some community plugins still depend on it in their byte code.
 */
public interface GeneralCompileCaches {
    Cache<HashCode, ClassSetAnalysisData> getClassSetAnalysisCache();
    Cache<HashCode, ClassAnalysis> getClassAnalysisCache();
}
