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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;

/**
 * A factory for creating instances of `MutableComponentResolveMetadata` for different repository formats.
 */
public interface MutableModuleMetadataFactory<S extends MutableModuleComponentResolveMetadata> {

    S createForGradleModuleMetadata(ModuleComponentIdentifier from);

    S missing(ModuleComponentIdentifier from);
}
