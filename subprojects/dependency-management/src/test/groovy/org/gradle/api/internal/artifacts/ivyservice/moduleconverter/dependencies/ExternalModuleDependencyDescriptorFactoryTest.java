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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ExternalModuleDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {

    ExternalModuleIvyDependencyDescriptorFactory externalModuleDependencyDescriptorFactory =
            new ExternalModuleIvyDependencyDescriptorFactory(excludeRuleConverterStub);

    @Test
    public void canConvert() {
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), Matchers.equalTo(false));
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), Matchers.equalTo(true));
    }

    @Test
    public void testAddWithNullGroupAndNullVersionShouldHaveEmptyStringModuleRevisionValues() {
        ModuleDependency dependency = new DefaultExternalModuleDependency(null, "gradle-core", null, TEST_DEP_CONF);
        DslOriginDependencyMetadata dependencyMetaData = externalModuleDependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, null, dependency);
        assertThat(dependencyMetaData.getRequested().getGroup(), equalTo(""));
        assertThat(dependencyMetaData.getRequested().getName(), equalTo("gradle-core"));
        assertThat(dependencyMetaData.getRequested().getVersion(), equalTo(""));
    }

    @Test
    public void testCreateFromModuleDependency() {
        DefaultExternalModuleDependency moduleDependency = new DefaultExternalModuleDependency("org.gradle",
                "gradle-core", "1.0", TEST_DEP_CONF);
        setUpDependency(moduleDependency);

        DslOriginDependencyMetadata dependencyMetaData = externalModuleDependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, null, moduleDependency);

        assertEquals(moduleDependency.isChanging(), dependencyMetaData.isChanging());
        assertEquals(moduleDependency.isForce(), dependencyMetaData.isForce());
        assertEquals(moduleDependency.getGroup(), dependencyMetaData.getRequested().getGroup());
        assertEquals(moduleDependency.getName(), dependencyMetaData.getRequested().getName());
        assertEquals(moduleDependency.getVersion(), dependencyMetaData.getRequested().getVersion());
        assertDependencyDescriptorHasCommonFixtureValues(dependencyMetaData);
    }
}
