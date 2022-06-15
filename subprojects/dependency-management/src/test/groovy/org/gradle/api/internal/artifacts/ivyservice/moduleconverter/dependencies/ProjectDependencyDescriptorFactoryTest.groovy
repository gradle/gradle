/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.Rule

class ProjectDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalSpec {

    private ProjectIvyDependencyDescriptorFactory projectDependencyDescriptorFactory = new ProjectIvyDependencyDescriptorFactory(excludeRuleConverterStub)
    private final ComponentIdentifier componentId = new ComponentIdentifier() {
        @Override
        String getDisplayName() {
            return "example"
        }
    }

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def canConvert() {
        expect:
        projectDependencyDescriptorFactory.canConvert(Mock(ProjectDependency))
        !projectDependencyDescriptorFactory.canConvert(Mock(ExternalModuleDependency))
    }

    def testCreateFromProjectDependency() {
        when:
        boolean withArtifacts = true
        ProjectDependency projectDependency = createProjectDependency(null)
        setUpDependency(projectDependency, withArtifacts)
        LocalOriginDependencyMetadata dependencyMetaData = projectDependencyDescriptorFactory.createDependencyDescriptor(componentId, TEST_CONF, null, projectDependency)

        then:
        assertDependencyDescriptorHasCommonFixtureValues(dependencyMetaData, withArtifacts)
        !dependencyMetaData.changing
        !dependencyMetaData.force
        dependencyMetaData.selector == new DefaultProjectComponentSelector(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root", ImmutableAttributes.EMPTY, [])
        projectDependency == dependencyMetaData.source
    }

    def testCreateFromProjectDependencyWithoutArtifacts() {
        when:
        boolean withArtifacts = false
        ProjectDependency projectDependency = createProjectDependency(TEST_DEP_CONF)
        setUpDependency(projectDependency, withArtifacts)
        LocalOriginDependencyMetadata dependencyMetaData = projectDependencyDescriptorFactory.createDependencyDescriptor(componentId, TEST_CONF, null, projectDependency)

        then:
        assertDependencyDescriptorHasCommonFixtureValues(dependencyMetaData, withArtifacts)
        !dependencyMetaData.changing
        !dependencyMetaData.force
        dependencyMetaData.selector == new DefaultProjectComponentSelector(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root", ImmutableAttributes.EMPTY, [])
        projectDependency == dependencyMetaData.source
    }

    private ProjectDependency createProjectDependency(String dependencyConfiguration) {
        Project dependencyProject = TestUtil.create(temporaryFolder).rootProject()
        dependencyProject.setGroup("someGroup")
        dependencyProject.setVersion("someVersion")
        if (dependencyConfiguration != null) {
            dependencyProject.configurations.create(dependencyConfiguration)
        }
        return new DefaultProjectDependency(dependencyProject, dependencyConfiguration, true)
    }
}
