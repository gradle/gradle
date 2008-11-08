/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.gradle.api.Project;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.initialization.ISettingsFinder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyManagerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    @Test public void testCreate() {
        Project expectedProject = new DefaultProject();
        final File testRootDir = new File("root");
        final File expectedBuildResolverDir = new File(testRootDir, Project.TMP_DIR_NAME + "/" + DependencyManager.BUILD_RESOLVER_NAME);
        final ISettingsFinder settingsFinderMock = context.mock(ISettingsFinder.class);
        context.checking(new Expectations() {{
            allowing(settingsFinderMock).getSettingsDir(); will(returnValue(testRootDir));
        }});
        DefaultDependencyManager dependencyManager = (DefaultDependencyManager)
                new DefaultDependencyManagerFactory(settingsFinderMock).createDependencyManager(expectedProject);
        // todo: check when ivy management has improved
        //assertNotNull(dependencyManager.ivy)
        assertSame(expectedProject, dependencyManager.getProject());
        assertNotNull(dependencyManager.getDependencyFactory());
        assertNotNull(dependencyManager.getSettingsConverter());
        assertNotNull(dependencyManager.getModuleDescriptorConverter());
        assertNotNull(dependencyManager.getDependencyPublisher());
        assertNotNull(dependencyManager.getDependencyResolver());
        assertEquals(expectedBuildResolverDir, dependencyManager.getBuildResolverHandler().getBuildResolverDir());
        Set<IDependencyImplementationFactory> dependencyImplementationFactories =
                dependencyManager.getDependencyFactory().getDependencyFactories();
        checkDependencyFactories(dependencyImplementationFactories);
    }

    private void checkDependencyFactories(Set<IDependencyImplementationFactory> dependencyImplementationFactories) {
        assertThat(dependencyImplementationFactories.size(), equalTo(2));
        boolean containsModule = false;
        boolean containsProject = false;
        for (IDependencyImplementationFactory dependencyImplementationFactory : dependencyImplementationFactories) {
            if (dependencyImplementationFactory instanceof ProjectDependencyFactory) {
                containsProject = true;
            } else if (dependencyImplementationFactory instanceof ModuleDependencyFactory) {
                containsModule = true;
            }
        }
        assertTrue(containsModule);
        assertTrue(containsProject);
    }

}
