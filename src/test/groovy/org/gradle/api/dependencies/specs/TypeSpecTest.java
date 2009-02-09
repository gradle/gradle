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
package org.gradle.api.dependencies.specs;

import org.gradle.api.dependencies.ModuleDependency;
import org.gradle.api.dependencies.ProjectDependency;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class TypeSpecTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void init() {
        assertSame(Type.PROJECT, new DependencyTypeSpec(Type.PROJECT).getType());
    }

    @Test
    public void testIsSatisfiedBy() {
        assertTrue(new DependencyTypeSpec(Type.PROJECT).isSatisfiedBy(context.mock(ProjectDependency.class)));
        assertFalse(new DependencyTypeSpec(Type.PROJECT).isSatisfiedBy(context.mock(ModuleDependency.class)));
    }

    @Test
    public void equality() {
        assertTrue(new DependencyTypeSpec(Type.PROJECT).equals(new DependencyTypeSpec(Type.PROJECT)));
        assertFalse(new DependencyTypeSpec(Type.PROJECT).equals(new DependencyTypeSpec(Type.EXTERNAL)));
    }
}
