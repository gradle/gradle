/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private IDependencyImplementationFactory testImplPointFactoryStub = context.mock(IDependencyImplementationFactory.class, "Point");
    private DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(
            WrapUtil.toSet(testImplPointFactoryStub), null, null);

    @Test
    public void testCreateDependencyWithValidDescription() {
        final Point point = createAnonymousPoint();
        final Dependency pointDependencyDummy = context.mock(Dependency.class, "PointDependency");
        context.checking(new Expectations() {{
            allowing(testImplPointFactoryStub).createDependency(Dependency.class, point);
            will(returnValue(pointDependencyDummy));
        }});
        assertSame(pointDependencyDummy, dependencyFactory.createDependency(point));
    }

    @Test
    public void createDependencyWithDependencyObject() {
        final Dependency dependencyDummy = context.mock(Dependency.class);
        assertSame(dependencyDummy, dependencyFactory.createDependency(dependencyDummy));    
    }

    @Test
    public void testCreateDependencyWithValidDescriptionAndClosure() {
        final Point point = createAnonymousPoint();
        final Dependency pointDependencyMock = context.mock(Dependency.class, "PointDependency");
        context.checking(new Expectations() {{
            allowing(testImplPointFactoryStub).createDependency(Dependency.class, point);
            will(returnValue(pointDependencyMock));
        }});
        assertSame(pointDependencyMock, dependencyFactory.createDependency(point));
    }

    private Point createAnonymousPoint() {
        return new Point(5, 4);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testCreateDependencyWithInvalidDescriptionShouldThrowInvalidUserDataEx() {
        final IDependencyImplementationFactory testImplStringFactoryStub = context.mock(IDependencyImplementationFactory.class, "String");
        context.checking(new Expectations() {{
            allowing(testImplPointFactoryStub).createDependency(with(equalTo(Dependency.class)), with(not(instanceOf(Point.class))));
            will(throwException(new IllegalDependencyNotation()));
            allowing(testImplStringFactoryStub).createDependency(with(equalTo(Dependency.class)), with(not(instanceOf(String.class))));
            will(throwException(new IllegalDependencyNotation()));
        }});
        dependencyFactory.createDependency(createAnonymousInteger());
    }

    private Integer createAnonymousInteger() {
        return new Integer(5);
    }

    @Test
    public void createProject() {
        final ProjectDependencyFactory projectDependencyFactoryStub = context.mock(ProjectDependencyFactory.class);
        final ProjectDependency projectDependency = context.mock(ProjectDependency.class);
        final ProjectFinder projectFinderDummy = context.mock(ProjectFinder.class);
        DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(null, null, projectDependencyFactoryStub);
        final Map map = WrapUtil.toMap("key", "value");
        context.checking(new Expectations() {{
            allowing(projectDependencyFactoryStub).createProjectDependencyFromMap(projectFinderDummy, map);
            will(returnValue(projectDependency));
        }});
        Closure configureClosure = HelperUtil.toClosure("{ transitive = false }");
        assertThat(dependencyFactory.createProjectDependencyFromMap(projectFinderDummy, map), sameInstance(projectDependency));
    }

    @Test
    public void createModule() {
        final IDependencyImplementationFactory testImplStringFactoryStub = context.mock(IDependencyImplementationFactory.class, "String");
        final IDependencyImplementationFactory clientModuleFactoryStub = context.mock(IDependencyImplementationFactory.class);
        final ClientModule clientModuleMock = context.mock(ClientModule.class);
        DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(WrapUtil.toSet(testImplStringFactoryStub), clientModuleFactoryStub, null);
        final String someNotation1 = "someNotation1";
        final String someNotation2 = "someNotation2";
        final String someNotation3 = "someNotation3";
        final String someNotation4 = "someNotation4";
        final String someModuleNotation = "junit:junit:4.4";
        final ModuleDependency dependencyDummy1 = context.mock(ModuleDependency.class, "dep1");
        final ModuleDependency dependencyDummy2 = context.mock(ModuleDependency.class, "dep2");
        final ModuleDependency dependencyDummy3 = context.mock(ModuleDependency.class, "dep3");
        final ModuleDependency dependencyMock = context.mock(ModuleDependency.class, "dep4");
        context.checking(new Expectations() {{
            allowing(clientModuleFactoryStub).createDependency(ClientModule.class, someModuleNotation);
            will(returnValue(clientModuleMock));
            allowing(testImplStringFactoryStub).createDependency(Dependency.class, someNotation1);
            will(returnValue(dependencyDummy1));
            allowing(testImplStringFactoryStub).createDependency(Dependency.class, someNotation2);
            will(returnValue(dependencyDummy2));
            allowing(testImplStringFactoryStub).createDependency(Dependency.class, someNotation3);
            will(returnValue(dependencyDummy3));
            allowing(testImplStringFactoryStub).createDependency(Dependency.class, someNotation4);
            will(returnValue(dependencyMock));
            one(dependencyMock).setTransitive(true);
            one(clientModuleMock).addDependency(dependencyDummy1);
            one(clientModuleMock).addDependency(dependencyDummy2);
            one(clientModuleMock).addDependency(dependencyDummy3);
            one(clientModuleMock).addDependency(dependencyMock);
        }});
        Closure configureClosure = HelperUtil.toClosure(String.format(
                "{dependency('%s'); dependencies('%s', '%s'); dependency('%s') { transitive = true }}",
                someNotation1, someNotation2, someNotation3, someNotation4));
        assertThat(dependencyFactory.createModule(someModuleNotation, configureClosure), equalTo(clientModuleMock));
    }

    @Test
    public void createModuleWithNullClosure() {
        final IDependencyImplementationFactory testImplStringFactoryStub = context.mock(IDependencyImplementationFactory.class, "String");
        final IDependencyImplementationFactory clientModuleFactoryStub = context.mock(IDependencyImplementationFactory.class);
        final ClientModule clientModuleMock = context.mock(ClientModule.class);
        DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(WrapUtil.toSet(testImplStringFactoryStub), clientModuleFactoryStub, null);

        final String someModuleNotation = "junit:junit:4.4";
        context.checking(new Expectations() {{
            allowing(clientModuleFactoryStub).createDependency(ClientModule.class, someModuleNotation);
            will(returnValue(clientModuleMock));
        }});
        assertThat(dependencyFactory.createModule(someModuleNotation, null), equalTo(clientModuleMock));
    }

}
