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

import org.gradle.api.Action;

/**
 * A component metadata rule is responsible for providing the initial metadata of a component
 * from a remote repository, in place of parsing the descriptor. Users may implement a provider
 * to make dependency resolution faster.
 *
 * @since 4.0
 */
public interface ComponentMetadataSupplier extends Action<ComponentMetadataSupplierDetails> {
    /**
     * Supply metadata for a component.
     *
     * @param details the supplier details
     */
    @Override
    void execute(ComponentMetadataSupplierDetails details);
}
