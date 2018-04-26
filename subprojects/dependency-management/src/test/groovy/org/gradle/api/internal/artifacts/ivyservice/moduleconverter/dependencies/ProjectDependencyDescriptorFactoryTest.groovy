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
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertThat

public class ProjectDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {

    private ProjectIvyDependencyDescriptorFactory projectDependencyDescriptorFactory =
            new ProjectIvyDependencyDescriptorFactory(excludeRuleConverterStub);
    private final ComponentIdentifier componentId = new OpaqueComponentIdentifier("foo")

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()


    @Test
    public void canConvert() {
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), equalTo(true));
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), equalTo(false));
    }

    @Test
    public void testCreateFromProjectDependency() {
        ProjectDependency projectDependency = createProjectDependency(TEST_DEP_CONF);
        setUpDependency(projectDependency);
        DslOriginDependencyMetadata dependencyMetaData = projectDependencyDescriptorFactory.createDependencyDescriptor(componentId, TEST_CONF, null, projectDependency);

        assertDependencyDescriptorHasCommonFixtureValues(dependencyMetaData);
        assertFalse(dependencyMetaData.isChanging());
        assertFalse(dependencyMetaData.isForce());
        assertEquals(new DefaultProjectComponentSelector(DefaultBuildIdentifier.ROOT, ":"), dependencyMetaData.getSelector());
        assertSame(projectDependency, dependencyMetaData.source);
    }

    private ProjectDependency createProjectDependency(String dependencyConfiguration) {
        Project dependencyProject = TestUtil.create(temporaryFolder).rootProject();
        dependencyProject.setGroup("someGroup");
        dependencyProject.setVersion("someVersion");
        dependencyProject.configurations.create(dependencyConfiguration)
        return new DefaultProjectDependency(dependencyProject, dependencyConfiguration, {} as ProjectAccessListener, true);
    }
}
