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

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.gradle.api.DependencyManager
import org.gradle.api.dependencies.ExcludeRuleContainer
import org.gradle.api.dependencies.PublishArtifact
import org.gradle.api.internal.dependencies.DefaultModuleDescriptorConverter
import org.gradle.api.internal.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.JUnit4GroovyMockery
import org.hamcrest.Matchers
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class DefaultModuleDescriptorConverterTest {
    DefaultModuleDescriptorConverter moduleDescriptorConverter
    DependencyManager dependencyManager
    ExcludeRule testExcludeRule1;
    ExcludeRule testExcludeRule2;
    ExcludeRuleContainer excludeRuleContainerMock;
    BuildResolverHandler buildResolverHandlerMock;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        excludeRuleContainerMock = context.mock(ExcludeRuleContainer)
        buildResolverHandlerMock = context.mock(BuildResolverHandler)
        moduleDescriptorConverter = new DefaultModuleDescriptorConverter()
        dependencyManager = new BaseDependencyManager(null, null, null, null, null, null, null, buildResolverHandlerMock,
                new DefaultExcludeRuleContainer())
        dependencyManager.project = new DefaultProject("someproject")
        dependencyManager.setExcludeRules(excludeRuleContainerMock)
        createTestExcludeRules()
    }

    private void createTestExcludeRules() {
        testExcludeRule1 = new DefaultExcludeRule(new ArtifactId(
                ModuleId.newInstance("org", "name"), "name", "type", "ext"),
                ExactPatternMatcher.INSTANCE,
                [:])
        testExcludeRule1.addConfiguration "conf1"
        testExcludeRule2 = new DefaultExcludeRule(new ArtifactId(
                ModuleId.newInstance("org2", "name2"), "name", "type", "ext"),
                ExactPatternMatcher.INSTANCE,
                [:])
        testExcludeRule2.addConfiguration "conf2"
        context.checking {
            allowing(excludeRuleContainerMock).createRules(withParam(Matchers.hasItems("conf1", "conf2"))); will(returnValue([testExcludeRule1, testExcludeRule2]))
        }
    }

    @Test public void testConvert() {
        Artifact ivyArtifact = [a: {}] as Artifact
        PublishArtifact gradleArtifact = [createIvyArtifact: {ivyArtifact}] as PublishArtifact
        Artifact ivyArtifact2 = [b: {}] as Artifact

        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        DefaultProjectDependency dependency = context.mock(DefaultProjectDependency)
        context.checking {
            allowing(dependency).createDependencyDescriptor(withParam(aNonNull(ModuleDescriptor))); will(returnValue(dependencyDescriptor))
        }
        DependencyDescriptor dependencyDescriptor2 = [:] as DependencyDescriptor

        dependencyManager.dependencies = [dependency]
        dependencyManager.dependencyDescriptors = [dependencyDescriptor2]
        dependencyManager.artifacts = [conf1: [gradleArtifact]]
        dependencyManager.artifactDescriptors = [conf1: [ivyArtifact2]]

        dependencyManager.project.group = 'group'
        dependencyManager.project.version = '1.1'
        dependencyManager.project.status = 'release'
        dependencyManager.addConfiguration('conf1')
        dependencyManager.addConfiguration('conf2')

        List expectedDepencencyDescriptors = [dependencyDescriptor, dependencyDescriptor2]
        Map expectedArtifactsDescriptors = [conf1: [ivyArtifact, ivyArtifact2]]

        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId(dependencyManager.project.group,
                dependencyManager.project.name), dependencyManager.project.version)

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(dependencyManager, true)

        assertEquals(moduleRevisionId, moduleDescriptor.moduleRevisionId)
        assertEquals(dependencyManager.project.status, moduleDescriptor.status)
        assertEquals(expectedDepencencyDescriptors as HashSet, moduleDescriptor.dependencies as HashSet)
        assertEquals(dependencyManager.configurations.values().collect {it.ivyConfiguration} as HashSet, moduleDescriptor.configurations as HashSet)
        assertEquals(expectedArtifactsDescriptors.conf1 as HashSet, moduleDescriptor.allArtifacts as HashSet)
        assertEquals([testExcludeRule1, testExcludeRule2], moduleDescriptor.getAllExcludeRules() as List)

        assertEquals([dependencyDescriptor2] as Set, moduleDescriptorConverter.convert(dependencyManager, false).dependencies as HashSet)
    }

    @Test public void testConvertWithDefaultStatus() {
        Artifact ivyArtifact = [a: {}] as Artifact
        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        DefaultProjectDependency dependency = context.mock(DefaultProjectDependency)
        context.checking {
            allowing(dependency).createDependencyDescriptor(withParam(aNonNull(ModuleDescriptor))); will(returnValue(dependencyDescriptor))
        }
        PublishArtifact gradleArtifact = [createIvyArtifact: {ivyArtifact}] as PublishArtifact
        dependencyManager.dependencies = [dependency]
        dependencyManager.artifacts = [conf1: [gradleArtifact]]

        dependencyManager.project.group = 'group'
        dependencyManager.project.version = '1.1'
        dependencyManager.addConfiguration('conf1')
        dependencyManager.addConfiguration('conf2')

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(dependencyManager, true)
        assertEquals(DependencyManager.DEFAULT_STATUS, moduleDescriptor.status)
    }


}
