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
package org.gradle.api.internal.dependencies.ivy;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.GradleException;
import org.gradle.api.dependencies.ResolveInstruction;
import org.gradle.api.internal.dependencies.ivy.Report2Classpath;
import org.gradle.api.internal.dependencies.ivy.DefaultIvyDependencyResolver;
import org.gradle.util.WrapUtil;
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
import java.util.HashMap;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultIvyDependencyResolverTest {
    public static final String TEST_CONF = "testConf";

    private DefaultIvyDependencyResolver ivyDependencyResolver;
    private Report2Classpath report2ClasspathMock;

    private JUnit4Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    private IvySettings expectedSettings;
    private Ivy ivyMock;
    private List<File> expectedClasspath;
    private ModuleDescriptor expectedModuleDescriptor;
    private ResolveOptions expectedResolveOptions;
    private ResolveInstruction testResolveInstruction;
    private ResolveOptionsFactory resolveOptionsFactoryMock;
    private ResolveReport resolveReportMock;

    @Before
    public void setUp() {
        testResolveInstruction = new ResolveInstruction();
        resolveOptionsFactoryMock = context.mock(ResolveOptionsFactory.class);
        report2ClasspathMock = context.mock(Report2Classpath.class);
        ivyDependencyResolver = new DefaultIvyDependencyResolver(resolveOptionsFactoryMock, report2ClasspathMock);
        ivyMock = context.mock(Ivy.class);
        expectedSettings = new IvySettings();
        expectedClasspath = WrapUtil.toList(new File(""));
        expectedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap()));
        resolveReportMock = context.mock(ResolveReport.class);
        expectedResolveOptions = new ResolveOptions();
        context.checking(new Expectations() {{
            allowing(resolveOptionsFactoryMock).createResolveOptions(TEST_CONF, testResolveInstruction);
            will(returnValue(expectedResolveOptions));
        }});
    }

    @Test
    public void testResolve() throws IOException, ParseException {
        prepareMocks(false);
        assertSame(expectedClasspath, ivyDependencyResolver.resolve(TEST_CONF, testResolveInstruction, ivyMock, expectedModuleDescriptor));
    }

    @Test
    public void testResolveFromReport() throws IOException, ParseException {
        prepareMocks(false);
        assertSame(expectedClasspath, ivyDependencyResolver.resolveFromReport(TEST_CONF, resolveReportMock));
    }


    @Test(expected = GradleException.class)
    public void testResolveWithMissingDependenciesAndFailTrue() throws IOException, ParseException {
        prepareMocks(true);
        testResolveInstruction.setFailOnResolveError(true);
        ivyDependencyResolver.resolve(TEST_CONF, testResolveInstruction, ivyMock, expectedModuleDescriptor);
    }

    @Test(expected = GradleException.class)
    public void testResolveAsReportWithMissingDependenciesAndFailTrue() throws IOException, ParseException {
        prepareMocks(true);
        testResolveInstruction.setFailOnResolveError(true);
        ivyDependencyResolver.resolveAsReport(TEST_CONF, testResolveInstruction, ivyMock, expectedModuleDescriptor);
    }

    @Test
    public void testResolveWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        prepareMocks(true);
        testResolveInstruction.setFailOnResolveError(false);
        assertSame(expectedClasspath, ivyDependencyResolver.resolve(TEST_CONF, testResolveInstruction, ivyMock, expectedModuleDescriptor));
    }

    @Test
    public void testResolveAsReportWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        prepareMocks(true);
        testResolveInstruction.setFailOnResolveError(false);
        assertSame(resolveReportMock, ivyDependencyResolver.resolveAsReport(TEST_CONF, testResolveInstruction, ivyMock, expectedModuleDescriptor));
    }

    private void prepareMocks(final boolean hasError) throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError();
                will(returnValue(hasError));
                allowing(ivyMock).resolve(expectedModuleDescriptor, expectedResolveOptions);
                will(returnValue(resolveReportMock));
                allowing(report2ClasspathMock).getClasspath(TEST_CONF, resolveReportMock);
                will(returnValue(expectedClasspath));
            }
        });
    }

}
