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
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.WrapUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertSame;
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
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultIvyDependencyResolverTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private Configuration configurationStub = context.mock(Configuration.class);
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
    public void testResolve() throws IOException, ParseException {
        prepareResolveReport(false);
        Set<File> expectedClasspath = WrapUtil.toSet(new File(""));
        prepareTestThatReturnClasspath(expectedClasspath);
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        
        assertSame(expectedClasspath, ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor));
    }

    @Test
    public void testResolveFromReport() throws IOException, ParseException {
        prepareResolveReport(false);
        Set<File> expectedClasspath = WrapUtil.toSet(new File(""));
        prepareTestThatReturnClasspath(expectedClasspath);
        
        assertSame(expectedClasspath, ivyDependencyResolver.resolveFromReport(configurationStub, resolveReportMock));
    }

    private void prepareTestThatReturnClasspath(final Set<File> expectedClasspath) throws IOException, ParseException {
        final String configurationName = configurationStub.getName();
        context.checking(new Expectations() {{
            allowing(report2ClasspathStub).getClasspath(configurationName, resolveReportMock);
            will(returnValue(expectedClasspath));
        }});
    }

    @Test(expected = GradleException.class)
    public void testResolveWithMissingDependencies_shouldThrowGradleEx() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        prepareResolveReport(true);
        ivyDependencyResolver.resolve(configurationStub, ivyStub, moduleDescriptor);
    }

    @Test(expected = GradleException.class)
    public void testResolveFromReportWithMissingDependencies_shouldThrowGradleEx() throws IOException, ParseException {
        prepareResolveReport(true);
        ivyDependencyResolver.resolveFromReport(configurationStub, resolveReportMock);
    }

    @Test
    public void testResolveAsReport() throws IOException, ParseException {
        prepareResolveReport(false);
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertSame(resolveReportMock, ivyDependencyResolver.resolveAsReport(configurationStub, ivyStub, moduleDescriptor, false));
    }

    @Test
    public void testResolveAsReportWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        prepareResolveReport(true);
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertSame(resolveReportMock, ivyDependencyResolver.resolveAsReport(configurationStub, ivyStub, moduleDescriptor, false));
    }

    @Test(expected = GradleException.class)
    public void testResolveAsReportWithMissingDependenciesAndFailTrue_shouldThrowGradleEx() throws IOException, ParseException {
        prepareResolveReport(true);
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        ivyDependencyResolver.resolveAsReport(configurationStub, ivyStub, moduleDescriptor, true);
    }

    private ModuleDescriptor createAnonymousModuleDescriptor() {
        return DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap()));
    }

    private void prepareResolveReport(final boolean hasError) throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError();
                will(returnValue(hasError));
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
