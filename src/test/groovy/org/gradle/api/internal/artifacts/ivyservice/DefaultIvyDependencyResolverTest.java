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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.gradle.api.GradleException;
import org.gradle.api.specs.Specs;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.ResolvedConfiguration;
import static org.gradle.util.WrapUtil.*;
import org.gradle.util.WrapUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyDependencyResolverTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private Configuration configurationStub = context.mock(Configuration.class, "<configuration>");
    private Ivy ivyStub = context.mock(Ivy.class);
    private Report2Classpath report2ClasspathStub = context.mock(Report2Classpath.class);
    private ResolveReport resolveReportMock = context.mock(ResolveReport.class);

    private DefaultIvyDependencyResolver ivyDependencyResolver = new DefaultIvyDependencyResolver(report2ClasspathStub);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(configurationStub).getName();
            will(returnValue("someConfName"));
        }});
    }

    @Test
    public void testResolveAndGetFiles() throws IOException, ParseException {
        prepareResolveReport();
        final Dependency dependencyDummy = context.mock(Dependency.class);
        final Set<File> expectedClasspath = toSet(new File(""));
        final String configurationName = configurationStub.getName();
        context.checking(new Expectations() {{
            allowing(configurationStub).getAllDependencies();
            will(returnValue(WrapUtil.toSet(dependencyDummy)));
            allowing(report2ClasspathStub).getClasspath(resolveReportMock, WrapUtil.toSet(dependencyDummy));
            will(returnValue(expectedClasspath));
        }});
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        
        assertSame(expectedClasspath, ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor).getFiles(
                Specs.SATISFIES_ALL));
    }

    @Test
    public void testResolveAndGetFilesWithMissingDependencies_shouldThrowGradleEx() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        prepareResolveReportWithError();
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor);
        try {
            configuration.getFiles(Specs.SATISFIES_ALL);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), startsWith("Could not resolve all dependencies for <configuration>"));
        }
    }

    @Test
    public void testResolveAndRethrowFailureWithMissingDependencies_shouldThrowGradleEx() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        prepareResolveReportWithError();
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub, ivyStub,
                moduleDescriptor);

        assertTrue(configuration.hasError());
        try {
            configuration.rethrowFailure();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), startsWith("Could not resolve all dependencies for <configuration>"));
        }
    }

    @Test
    public void testResolveAndRethrowFailureWithNoMissingDependencies_shouldDoNothing() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        prepareResolveReport();
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub, ivyStub,
                moduleDescriptor);

        assertFalse(configuration.hasError());
        configuration.rethrowFailure();
    }

    @Test
    public void testResolveAndGetReport() throws IOException, ParseException {
        prepareResolveReport();
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertSame(resolveReportMock, ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor).getResolveReport());
    }

    @Test
    public void testResolveAndGetReportWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        prepareResolveReportWithError();
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertSame(resolveReportMock, ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor).getResolveReport());
    }

    private ModuleDescriptor createAnonymousModuleDescriptor() {
        return DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap()));
    }

    private void prepareResolveReport() throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError();
                will(returnValue(false));
            }
        });
    }

    private void prepareResolveReportWithError() throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError();
                will(returnValue(true));
                allowing(resolveReportMock).getAllProblemMessages();
                will(returnValue(toList("a problem")));
            }
        });
    }

    private void prepareTestsThatRetrieveDependencies(final ModuleDescriptor moduleDescriptor) throws IOException, ParseException {
        final String confName = configurationStub.getName();
        context.checking(new Expectations() {
            {
                allowing(ivyStub).resolve(with(equal(moduleDescriptor)), with(equaltResolveOptions(confName)));
                will(returnValue(resolveReportMock));
            }
        });
    }

    Matcher<ResolveOptions> equaltResolveOptions(final String... confs) {
         return new BaseMatcher<ResolveOptions>()  {
             public boolean matches(Object o) {
                 ResolveOptions otherOptions = (ResolveOptions) o;
                 return Arrays.equals(confs, otherOptions.getConfs());
             }

             public void describeTo(Description description) {
                 description.appendText("Checking Resolveoptions");
             }
         };
    }

}
