/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.TransformRegistration;

/**
 * The transforms registered for given project/dependency management instance.
 * <p>
 * Includes a secondary cacheable representation that can be used across project boundaries,
 * containing only cacheable data required to performing transform selection.
 */
public class RegisteredTransforms {

    private final ImmutableList<TransformRegistration> transforms;
    private final CacheableTransforms cacheableTransforms;

    public RegisteredTransforms(ImmutableList<TransformRegistration> transforms, CacheableTransforms cacheableTransforms) {
        this.transforms = transforms;
        this.cacheableTransforms = cacheableTransforms;
    }

    /**
     * The registered transforms.
     */
    public ImmutableList<TransformRegistration> getTransforms() {
        return transforms;
    }

    /**
     * Cacheable representation of the registered transforms containing only cacheable
     * data required to performing transform selection.
     */
    public CacheableTransforms getCacheableTransforms() {
        return cacheableTransforms;
    }

}
