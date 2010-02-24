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

import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.hamcrest.Matchers;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DefaultMavenPomFactoryTest {
    private Mockery context = new JUnit4Mockery();
    @Test
    public void createMavenPom() {
        DefaultConf2ScopeMappingContainer scopeMappings = new DefaultConf2ScopeMappingContainer();
        PomDependenciesConverter pomDependenciesConverter = context.mock(PomDependenciesConverter.class); 
        DefaultMavenPomFactory mavenPomFactory = new DefaultMavenPomFactory(scopeMappings, pomDependenciesConverter);
        DefaultMavenPom mavenPom = (DefaultMavenPom) mavenPomFactory.createMavenPom();
        assertNotSame(scopeMappings, mavenPom.getScopeMappings());
        assertEquals(scopeMappings, mavenPom.getScopeMappings());
        assertThat(mavenPom.getMavenProject(), notNullValue());
        assertThat(mavenPom.getPomDependenciesConverter(), Matchers.sameInstance(pomDependenciesConverter));
    }
}
