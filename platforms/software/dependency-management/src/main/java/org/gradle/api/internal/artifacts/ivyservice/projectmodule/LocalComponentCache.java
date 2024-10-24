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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.util.Path;

import java.util.function.Function;

/**
 * A cache for {@link LocalComponentGraphResolveState} instances.
 */
public interface LocalComponentCache {

    /**
     * A cache that does not perform caching and always executes the provided factory.
     */
    LocalComponentCache NO_CACHE = (path, factory) -> factory.apply(path);

    /**
     * Loads cached dependency resolution metadata for the given project, if available,
     * or else runs the given function to create it and then writes the result to the cache.
     */
    LocalComponentGraphResolveState computeIfAbsent(Path projectIdentityPath, Function<Path, LocalComponentGraphResolveState> factory);
}
