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

package org.gradle.internal.component.external.model

import org.gradle.api.Action
import org.gradle.api.artifacts.DependenciesMetadata

class DirectDependencyMetadataRulesTest extends AbstractDependencyMetadataRulesTest {
    @Override
    boolean addAllDependenciesAsConstraints() {
        return false
    }

    @Override
    void doAddDependencyMetadataRule(MutableModuleComponentResolveMetadata metadataImplementation, String variantName, Action<? super DependenciesMetadata> action) {
        metadataImplementation.variantMetadataRules.addDependencyAction(instantiator, notationParser, constraintNotationParser, variantAction(variantName, action))
    }
}
