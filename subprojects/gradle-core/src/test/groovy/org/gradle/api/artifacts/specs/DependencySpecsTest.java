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
package org.gradle.api.artifacts.specs;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.gradle.util.Matchers.strictlyEqual;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DependencySpecsTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testIsSatisfiedBy() {
        assertTrue(DependencySpecs.type(Type.PROJECT).isSatisfiedBy(context.mock(ProjectDependency.class)));
        assertFalse(DependencySpecs.type(Type.PROJECT).isSatisfiedBy(context.mock(ExternalModuleDependency.class)));
    }

    @Test
    public void equality() {
        assertThat(DependencySpecs.type(Type.PROJECT), strictlyEqual(DependencySpecs.type(Type.PROJECT)));
        assertFalse(DependencySpecs.type(Type.PROJECT).equals(DependencySpecs.type(Type.EXTERNAL)));
    }
}
