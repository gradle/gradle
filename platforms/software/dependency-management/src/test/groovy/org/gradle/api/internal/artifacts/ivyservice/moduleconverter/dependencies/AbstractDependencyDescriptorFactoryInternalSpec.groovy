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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification

abstract class AbstractDependencyDescriptorFactoryInternalSpec extends Specification {
    static final TEST_DEP_CONF = "depconf1"

    static final TEST_EXCLUDE_RULE = new org.gradle.api.internal.artifacts.DefaultExcludeRule("testOrg", null)
    static final TEST_IVY_EXCLUDE_RULE = getTestExcludeRule()

    static final ARTIFACT = new DefaultDependencyArtifact("name", "type", "classifier", "ext", null)
    static final ARTIFACT_WITH_CLASSIFIERS = new DefaultDependencyArtifact("name2", "type2", "ext2", "classifier2", "http://www.url2.com")

    def excludeRuleConverterStub = Mock(ExcludeRuleConverter)

    def setup() {
        expectExcludeRuleConversion(TEST_EXCLUDE_RULE, TEST_IVY_EXCLUDE_RULE)
    }

    protected void expectExcludeRuleConversion(final ExcludeRule excludeRule, final Exclude exclude) {
        excludeRuleConverterStub.convertExcludeRule(excludeRule) >> exclude
    }

    protected static Dependency setUpDependency(ModuleDependency dependency, boolean withArtifacts) {
        ModuleDependency result = dependency;
        if (withArtifacts) {
            result = dependency.addArtifact(ARTIFACT).
                addArtifact(ARTIFACT_WITH_CLASSIFIERS)
        }
        return result.exclude(WrapUtil.toMap("group", TEST_EXCLUDE_RULE.getGroup())).
                setTransitive(true)
    }

    protected static void assertDependencyDescriptorHasCommonFixtureValues(LocalOriginDependencyMetadata dependencyMetadata, boolean withArtifacts) {
        assert TEST_IVY_EXCLUDE_RULE == dependencyMetadata.getExcludes().get(0)
        if (!withArtifacts) {
            assert dependencyMetadata.getDependencyConfiguration() == TEST_DEP_CONF
        }
        assert dependencyMetadata.isTransitive()
        if (withArtifacts) {
            assertDependencyDescriptorHasArtifacts(dependencyMetadata)
        }
    }

    private static void assertDependencyDescriptorHasArtifacts(DependencyMetadata dependencyMetadata) {
        List<IvyArtifactName> artifactDescriptors = WrapUtil.toList(dependencyMetadata.getArtifacts())
        assert artifactDescriptors.size() == 2

        IvyArtifactName artifactDescriptorWithoutClassifier = findDescriptor(artifactDescriptors, ARTIFACT)
        compareArtifacts(ARTIFACT, artifactDescriptorWithoutClassifier)
        assert artifactDescriptorWithoutClassifier.classifier == ARTIFACT.classifier
        assert artifactDescriptorWithoutClassifier.extension == ARTIFACT.extension

        IvyArtifactName artifactDescriptorWithClassifierAndConfs = findDescriptor(artifactDescriptors, ARTIFACT_WITH_CLASSIFIERS)
        compareArtifacts(ARTIFACT_WITH_CLASSIFIERS, artifactDescriptorWithClassifierAndConfs)
        assert ARTIFACT_WITH_CLASSIFIERS.classifier == artifactDescriptorWithClassifierAndConfs.classifier
        assert ARTIFACT_WITH_CLASSIFIERS.extension == artifactDescriptorWithClassifierAndConfs.extension
    }

    private static IvyArtifactName findDescriptor(List<IvyArtifactName> artifactDescriptors, DefaultDependencyArtifact dependencyArtifact) {
        for (IvyArtifactName artifactDescriptor : artifactDescriptors) {
            if (artifactDescriptor.getName().equals(dependencyArtifact.getName())) {
                return artifactDescriptor
            }
        }
        throw new RuntimeException("Descriptor could not be found")
    }

    private static void compareArtifacts(DependencyArtifact artifact, IvyArtifactName artifactDescriptor) {
        assert artifact.name == artifactDescriptor.name
        assert artifact.type == artifactDescriptor.type
    }

    private static DefaultExclude getTestExcludeRule() {
        return new DefaultExclude(DefaultModuleIdentifier.newId("org", "testOrg"))
    }
}

