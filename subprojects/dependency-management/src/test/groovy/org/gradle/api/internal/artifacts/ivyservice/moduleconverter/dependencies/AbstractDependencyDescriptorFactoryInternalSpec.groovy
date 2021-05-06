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

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
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
    static final TEST_CONF = "conf"
    static final TEST_DEP_CONF = "depconf1"

    static final TEST_EXCLUDE_RULE = new org.gradle.api.internal.artifacts.DefaultExcludeRule("testOrg", null)
    static final TEST_IVY_EXCLUDE_RULE = getTestExcludeRule()

    def excludeRuleConverterStub = Mock(ExcludeRuleConverter)
    def moduleDescriptor = createModuleDescriptor(WrapUtil.toSet(TEST_CONF))
    def artifact = new DefaultDependencyArtifact("name", "type", null, null, null)
    def artifactWithClassifiers = new DefaultDependencyArtifact("name2", "type2", "ext2", "classifier2", "http://www.url2.com")

    def setup() {
        expectExcludeRuleConversion(TEST_EXCLUDE_RULE, TEST_IVY_EXCLUDE_RULE)
    }

    static DefaultModuleDescriptor createModuleDescriptor(Set<String> confs) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(new ModuleRevisionId(new ModuleId("org", "name"), "rev"), "status", null)
        for (String conf : confs) {
            moduleDescriptor.addConfiguration(new Configuration(conf))
        }
        return moduleDescriptor
    }

    protected void expectExcludeRuleConversion(final ExcludeRule excludeRule, final Exclude exclude) {
        excludeRuleConverterStub.convertExcludeRule(excludeRule) >> exclude
    }

    protected Dependency setUpDependency(ModuleDependency dependency, boolean withArtifacts) {
        ModuleDependency result = dependency;
        if (withArtifacts) {
            result = dependency.addArtifact(artifact).
                addArtifact(artifactWithClassifiers)
        }
        return result.exclude(WrapUtil.toMap("group", TEST_EXCLUDE_RULE.getGroup())).
                setTransitive(true)
    }

    protected void assertDependencyDescriptorHasCommonFixtureValues(LocalOriginDependencyMetadata dependencyMetadata, boolean withArtifacts) {
        assert TEST_IVY_EXCLUDE_RULE == dependencyMetadata.getExcludes().get(0)
        assert dependencyMetadata.getModuleConfiguration() == TEST_CONF
        if (!withArtifacts) {
            assert dependencyMetadata.getDependencyConfiguration() == TEST_DEP_CONF
        }
        assert dependencyMetadata.isTransitive()
        if (withArtifacts) {
            assertDependencyDescriptorHasArtifacts(dependencyMetadata)
        }
    }

    private void assertDependencyDescriptorHasArtifacts(DependencyMetadata dependencyMetadata) {
        List<IvyArtifactName> artifactDescriptors = WrapUtil.toList(dependencyMetadata.getArtifacts())
        assert artifactDescriptors.size() == 2

        IvyArtifactName artifactDescriptorWithoutClassifier = findDescriptor(artifactDescriptors, artifact)
        compareArtifacts(artifact, artifactDescriptorWithoutClassifier)
        assert artifactDescriptorWithoutClassifier.classifier == null
        assert artifactDescriptorWithoutClassifier.extension == artifact.type

        IvyArtifactName artifactDescriptorWithClassifierAndConfs = findDescriptor(artifactDescriptors, artifactWithClassifiers)
        compareArtifacts(artifactWithClassifiers, artifactDescriptorWithClassifierAndConfs)
        assert artifactWithClassifiers.classifier == artifactDescriptorWithClassifierAndConfs.classifier
        assert artifactWithClassifiers.extension == artifactDescriptorWithClassifierAndConfs.extension
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

