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
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;

/**
 * A component metadata rule is responsible for providing the initial metadata of a component
 * from a remote repository, in place of parsing the descriptor. Users may implement a provider
 * to make dependency resolution faster.
 *
 * @since 3.5
 */
@Incubating
public interface ComponentMetadataRule {
    /**
     * Supply metadata for a component. Users can determine which component is requested by calling
     * {@link ComponentMetadataBuilder#getId()}, and have access to the remote repository through
     * the provided {@link RepositoryResourceAccessor}. This accessor caches external resources
     * following the timeout semantics of the queried module.
     *
     * @param details the rule details
     */
    void supply(ComponentMetadataRuleDetails details);
}
