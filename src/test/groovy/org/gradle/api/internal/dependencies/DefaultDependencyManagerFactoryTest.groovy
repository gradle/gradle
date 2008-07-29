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

package org.gradle.api.internal.dependencies

import org.gradle.api.dependencies.ArtifactDependency
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.api.dependencies.ProjectDependency
import static org.junit.Assert.*
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class DefaultDependencyManagerFactoryTest {
    @Test public void testCreate() {
        File rootDir = new File('root')
        DefaultDependencyManager dependencyManager = new DefaultDependencyManagerFactory(new File('root')).createDependencyManager()
        // todo: check when ivy management has improved
        //assertNotNull(dependencyManager.ivy)
        assertNotNull(dependencyManager.dependencyFactory)
        assertNotNull(dependencyManager.artifactFactory)
        assertNotNull(dependencyManager.settingsConverter)
        assertNotNull(dependencyManager.moduleDescriptorConverter)
        assertNotNull(dependencyManager.dependencyPublisher)
        assertNotNull(dependencyManager.dependencyResolver)
        assertEquals([ArtifactDependency, ModuleDependency, ProjectDependency], dependencyManager.dependencyFactory.dependencyImplementations)
        assertEquals(rootDir, dependencyManager.buildResolverDir)
    }
    
}
