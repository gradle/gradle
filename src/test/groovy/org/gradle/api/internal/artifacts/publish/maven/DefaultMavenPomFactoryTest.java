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
package org.gradle.api.internal.artifacts.publish.maven;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.util.HelperUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DefaultMavenPomFactoryTest {
    @Test
    public void createMavenPom() {
        DefaultConf2ScopeMappingContainer scopeMappings = new DefaultConf2ScopeMappingContainer();
        final DefaultModuleDescriptor testModuleDescriptor = DefaultModuleDescriptor.newBasicInstance(ModuleRevisionId.newInstance("org", "name","version"), null);
        testModuleDescriptor.addDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "name1", "rev1"), false));
        scopeMappings.addMapping(10, HelperUtil.createConfiguration("conf"), "scope");
        DefaultMavenPomFactory mavenPomFactory = new DefaultMavenPomFactory(scopeMappings);
        DefaultMavenPom mavenPom = (DefaultMavenPom) mavenPomFactory.createMavenPom();
        assertNotSame(scopeMappings, mavenPom.getScopeMappings());
        assertEquals(scopeMappings, mavenPom.getScopeMappings());
    }
}
