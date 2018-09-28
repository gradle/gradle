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
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.util.TestUtil

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class DependencyConstraintMetadataRulesTest extends AbstractDependencyMetadataRulesTest {
    private final mavenMetadataFactory = new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())

    @Override
    boolean addAllDependenciesAsConstraints() {
        return true
    }

    @Override
    void doAddDependencyMetadataRule(MutableModuleComponentResolveMetadata metadataImplementation, String variantName, Action<? super DependenciesMetadata> action) {
        metadataImplementation.variantMetadataRules.addDependencyConstraintAction(
            instantiator, notationParser, constraintNotationParser,
            variantAction(variantName, action))
    }

    def "maven optional dependencies are not accessible as dependency constraints"() {
        given:
        def mavenMetadata = mavenMetadataFactory.create(componentIdentifier, [
            new MavenDependencyDescriptor(MavenScope.Compile, MavenDependencyType.DEPENDENCY, newSelector(DefaultModuleIdentifier.newId("org", "notOptional"), "1.0"), null, []),
            new MavenDependencyDescriptor(MavenScope.Compile, MavenDependencyType.OPTIONAL_DEPENDENCY, newSelector(DefaultModuleIdentifier.newId("org", "optional"), "1.0"), null, [])
        ])

        when:
        mavenMetadata.variantMetadataRules.setVariantDerivationStrategy(new JavaEcosystemVariantDerivationStrategy())
        mavenMetadata.variantMetadataRules.addDependencyAction(instantiator, notationParser, constraintNotationParser, variantAction("default", {
            assert it.size() == 1
            assert it[0].name == "notOptional"
        }))
        mavenMetadata.variantMetadataRules.addDependencyConstraintAction(instantiator, notationParser, constraintNotationParser, variantAction("default", {
            assert it.size() == 1
            assert it[0].name == "optional"
        }))

        then:
        def dependencies = selectTargetConfigurationMetadata(mavenMetadata).dependencies
        dependencies.size() == 1
        !dependencies[0].constraint
    }
}
