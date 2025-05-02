/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.internal.reflect.Instantiator;

/**
 * Provides context for metadata resolution, sourced from a repository definition.
 *
 * @see ComponentMetadataProcessorFactory
 */
public interface MetadataResolutionContext {

    /**
     * The cache policy of a repository
     *
     * @return the cache policy
     */
    CacheExpirationControl getCacheExpirationControl();

    /**
     * Provides an injecting instantiator, giving access to services potentially contextual to a repository.
     *
     * @return a injecting instantiator
     */
    Instantiator getInjectingInstantiator();
}
