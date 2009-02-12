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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.not;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DependencyFactoryTest {
    private static final String TEST_CONFIGURATION = "testconf";

    private DependencyFactory dependencyFactory;

    private IDependencyImplementationFactory testImplIntegerFactoryMock;
    private IDependencyImplementationFactory testImplStringFactoryMock;

    private DefaultProject project;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before public void setUp() {
        project = new DefaultProject("projectName");
        testImplIntegerFactoryMock = context.mock(IDependencyImplementationFactory.class, "Integer");
        testImplStringFactoryMock = context.mock(IDependencyImplementationFactory.class, "String");
        Set<IDependencyImplementationFactory> dependencyFactories = WrapUtil.toSet(testImplIntegerFactoryMock,
                testImplStringFactoryMock);
        dependencyFactory = new DependencyFactory(dependencyFactories);
    }

    @Test public void testCreateDependencyWithValidDescription() {
        final Dependency expectedIntegerDependency = context.mock(Dependency.class, "IntegerDependency");
        final Dependency expectedStringDependency = context.mock(Dependency.class, "StringDependency");
        context.checking(new Expectations() {{
            allowing(testImplIntegerFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(an(Integer.class)),
                    with(same(project)));
            will(returnValue(expectedIntegerDependency));
            allowing(testImplIntegerFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(not(Integer.class)),
                    with(same(project)));
            will(throwException(new UnknownDependencyNotation()));
            allowing(testImplStringFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(an(String.class)),
                    with(same(project)));
            will(returnValue(expectedStringDependency));
            allowing(testImplStringFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(not(String.class)),
                    with(same(project)));
            will(throwException(new UnknownDependencyNotation()));
        }});

        assertSame(expectedIntegerDependency, dependencyFactory.createDependency(
                AbstractDependencyTest.TEST_CONF_MAPPING, new Integer(5), project));
        assertSame(expectedStringDependency, dependencyFactory.createDependency(
                AbstractDependencyTest.TEST_CONF_MAPPING, "somestring", project));
    }

    @Test (expected = InvalidUserDataException.class) public void testCreateDependencyWithInvalidDescription() {
        context.checking(new Expectations() {{
            allowing(testImplIntegerFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(not(Integer.class)),
                    with(same(project)));
            will(throwException(new UnknownDependencyNotation()));
            allowing(testImplStringFactoryMock).createDependency(
                    with(equal(AbstractDependencyTest.TEST_CONF_MAPPING)),
                    with(not(String.class)),
                    with(same(project)));
            will(throwException(new UnknownDependencyNotation()));
        }});
        dependencyFactory.createDependency(AbstractDependencyTest.TEST_CONF_MAPPING, new Point(3, 4), project);
    }
}
