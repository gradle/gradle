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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.DependencyManager
import org.gradle.api.dependencies.Dependency
import org.gradle.api.dependencies.GradleArtifact
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.ModuleDescriptorConverter
import org.gradle.api.internal.project.DefaultProject

/**
* @author Hans Dockter
*/
class ModuleDescriptorConverterTest extends GroovyTestCase {
    ModuleDescriptorConverter moduleDescriptorConverter
    DependencyManager dependencyManager

    void setUp() {
        moduleDescriptorConverter = new ModuleDescriptorConverter()
        dependencyManager = new DefaultDependencyManager(null, null, null, null, null, null)
        dependencyManager.project = new DefaultProject()
    }

    void testConvert() {
        Artifact ivyArtifact = [a:{}] as Artifact
        GradleArtifact gradleArtifact = [createIvyArtifact: {ivyArtifact}] as GradleArtifact
        Artifact ivyArtifact2 = [b:{}] as Artifact

        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        Dependency dependency = [createDepencencyDescriptor: {dependencyDescriptor}] as Dependency
        DependencyDescriptor dependencyDescriptor2 = [:] as DependencyDescriptor

        dependencyManager.dependencies = [dependency]
        dependencyManager.dependencyDescriptors = [dependencyDescriptor2]
        dependencyManager.artifacts = [conf1: [gradleArtifact]]
        dependencyManager.artifactDescriptors = [conf1: [ivyArtifact2]]

        dependencyManager.project.group = 'group'
        dependencyManager.project.version = '1.1'
        dependencyManager.project.name = 'someproject'
        dependencyManager.project.status = 'release'
        dependencyManager.configurations = [conf1: new Configuration('conf1'), conf2: new Configuration('conf2')]

        List expectedDepencencyDescriptors = [dependencyDescriptor, dependencyDescriptor2] 
        Map expectedArtifactsDescriptors = [conf1: [ivyArtifact, ivyArtifact2]]

        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId(dependencyManager.project.group,
                dependencyManager.project.name), dependencyManager.project.version)

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(dependencyManager)
        
        assertEquals(moduleRevisionId, moduleDescriptor.moduleRevisionId)
        assertEquals(dependencyManager.project.status, moduleDescriptor.status)
        assertEquals(expectedDepencencyDescriptors as HashSet, moduleDescriptor.dependencies as HashSet)
        assertEquals(dependencyManager.configurations.values() as HashSet, moduleDescriptor.configurations as HashSet)
        assertEquals(expectedArtifactsDescriptors.conf1 as HashSet, moduleDescriptor.allArtifacts as HashSet)
    }
}
