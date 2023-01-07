/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.List;

/**
 * Represents something that can be resolved.
 */
public interface ResolveContext {

    String getName();

    String getDisplayName();

    ResolutionStrategyInternal getResolutionStrategy();

    LocalComponentMetadata toRootComponentMetaData();

    AttributeContainer getAttributes();

    /**
     * Returns the synthetic dependencies for this context. Synthetic dependencies are dependencies which
     * are an internal implementation detail of Gradle, used for example in dependency locking or
     * consistent resolution. They are not "real" dependencies in the sense that they are not added by
     * users, and they are not always used during resolution based on which phase of execution we are
     * (task dependencies, execution, ...)
     */
    List<? extends DependencyMetadata> getSyntheticDependencies();
}
