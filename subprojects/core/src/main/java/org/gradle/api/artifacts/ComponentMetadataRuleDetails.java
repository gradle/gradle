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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;

/**
 * A component metadata rule details, giving access to the identifier of the component being
 * resolved, the metadata builder, and the repository resource accessor for this.
 *
 * @since 3.5
 */
@Incubating
public interface ComponentMetadataRuleDetails {
    /**
     * Returns the identifier of the component being resolved
     * @return the identifier
     */
    ModuleComponentIdentifier getId();

    /**
     * Returns the metadata builder, that users can use to feed metadata for the component.
     * @return the metadata builder
     */
    ComponentMetadataBuilder getResult();

    /**
     * A remote repository remote accessor, that gives access to remote resources and caches
     * the result based on the component cache policy.
     *
     * @return the repository resource accessor
     */
    RepositoryResourceAccessor getRepositoryResourceAccessor();
}
