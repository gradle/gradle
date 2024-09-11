/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.util.Path;

/**
 * Internal counterpart to {@link ProjectComponentSelector}
 */
public interface ProjectComponentSelectorInternal extends ProjectComponentSelector {

    @Override
    ImmutableAttributes getAttributes();

    /**
     * Returns a unique path for the target project within the current build tree.
     */
    Path getIdentityPath();

    /**
     * Returns the identity of the target project.
     */
    ProjectIdentity getProjectIdentity();
}
