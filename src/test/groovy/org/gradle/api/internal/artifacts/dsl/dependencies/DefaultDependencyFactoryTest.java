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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyFactory;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;

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
            allowing(testImplPointFactoryStub).createDependency(point);
            will(returnValue(pointDependencyDummy));
        }});
        assertSame(pointDependencyDummy, dependencyFactory.createDependency(point));
    }

    @Test
    public void testCreateDependencyWithValidDescriptionAndClosure() {
        final Point point = createAnonymousPoint();
        final Dependency pointDependencyMock = context.mock(Dependency.class, "PointDependency");
        context.checking(new Expectations() {{
            allowing(testImplPointFactoryStub).createDependency(point);
            will(returnValue(pointDependencyMock));
            one(pointDependencyMock).setTransitive(true);
        }});
        Closure configureClosure = HelperUtil.toClosure("{ transitive = true }");
        assertSame(pointDependencyMock, dependencyFactory.createDependency(point, configureClosure));
    }

    private Point createAnonymousPoint() {
        return new Point(5,4);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testCreateDependencyWithInvalidDescription_shouldThrowInvalidUserDataEx() {
        final IDependencyImplementationFactory testImplStringFactoryStub = context.mock(IDependencyImplementationFactory.class, "String");
        context.checking(new Expectations() {{
            allowing(testImplPointFactoryStub).createDependency(with(not(instanceOf(Point.class))));
            will(throwException(new IllegalDependencyNotation()));
            allowing(testImplStringFactoryStub).createDependency(with(not(instanceOf(String.class))));
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
        context.checking(new Expectations() {{
            allowing(projectDependencyFactoryStub).createProject(projectFinderDummy, "notation");
            will(returnValue(projectDependency));
            one(projectDependency).setTransitive(false);
        }});
        Closure configureClosure = HelperUtil.toClosure("{ transitive = false }");
        assertThat(dependencyFactory.createProject(projectFinderDummy, "notation", configureClosure), sameInstance(projectDependency));
    }

    @Test
    public void createModule() {
        final IDependencyImplementationFactory testImplStringFactoryStub = context.mock(IDependencyImplementationFactory.class, "String");
        final ClientModuleFactory clientModuleFactoryStub = context.mock(ClientModuleFactory.class);
        final ClientModule clientModuleMock = context.mock(ClientModule.class);
        DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(WrapUtil.toSet(testImplStringFactoryStub), clientModuleFactoryStub, null);
        final String someNotation1 = "someNotation1";
        final String someNotation2 = "someNotation2";
        final String someNotation3 = "someNotation3";
        final String someNotation4 = "someNotation4";
        final String someModuleNotation = "junit:junit:4.4";
        final Dependency dependencyDummy1 = context.mock(Dependency.class, "dep1");
        final Dependency dependencyDummy2 = context.mock(Dependency.class, "dep2");
        final Dependency dependencyDummy3 = context.mock(Dependency.class, "dep3");
        final Dependency dependencyMock = context.mock(Dependency.class, "dep4");
        context.checking(new Expectations() {{
            allowing(clientModuleFactoryStub).createClientModule(someModuleNotation);
            will(returnValue(clientModuleMock));
            allowing(testImplStringFactoryStub).createDependency(someNotation1);
            will(returnValue(dependencyDummy1));
            allowing(testImplStringFactoryStub).createDependency(someNotation2);
            will(returnValue(dependencyDummy2));
            allowing(testImplStringFactoryStub).createDependency(someNotation3);
            will(returnValue(dependencyDummy3));
            allowing(testImplStringFactoryStub).createDependency(with(equal(someNotation4)));
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

}
