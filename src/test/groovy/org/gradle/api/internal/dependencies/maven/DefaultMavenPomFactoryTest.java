/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.maven;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.DependencyManager;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;

import java.util.Arrays;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultMavenPomFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void createMavenPom() {
        PomModuleDescriptorFileWriter pomModuleDescriptorFileWriterMock = context.mock(PomModuleDescriptorFileWriter.class);
        final DependencyManager dependencyManagerMock = context.mock(DependencyManager.class);
        DefaultConf2ScopeMappingContainer scopeMappings = new DefaultConf2ScopeMappingContainer();
        final DefaultModuleDescriptor testModuleDescriptor = DefaultModuleDescriptor.newBasicInstance(ModuleRevisionId.newInstance("org", "name","version"), null);
        testModuleDescriptor.addDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "name1", "rev1"), false));
        context.checking(new Expectations() {{
            allowing(dependencyManagerMock).createModuleDescriptor(true); will(returnValue(testModuleDescriptor));
        }});
        scopeMappings.addMapping(10, "conf", "scope");
        DefaultMavenPomFactory mavenPomFactory = new DefaultMavenPomFactory(scopeMappings, dependencyManagerMock, pomModuleDescriptorFileWriterMock);
        DefaultMavenPom mavenPom = (DefaultMavenPom) mavenPomFactory.createMavenPom();
        assertNotSame(scopeMappings, mavenPom.getScopeMappings());
        assertEquals(scopeMappings, mavenPom.getScopeMappings());
        assertEquals(Arrays.asList(testModuleDescriptor.getDependencies()), mavenPom.getDependencies());
        assertSame(pomModuleDescriptorFileWriterMock, mavenPom.getPomModuleDescriptorFileWriter());
    }
}
