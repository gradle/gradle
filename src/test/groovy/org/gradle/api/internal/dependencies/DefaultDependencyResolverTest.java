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
package org.gradle.api.internal.dependencies;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertSame;
import org.junit.runner.RunWith;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.Ivy;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.util.WrapUtil;

import static org.gradle.util.ReflectionEqualsMatcher.reflectionEquals;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.GradleException;

import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultDependencyResolverTest {
    public static final String TEST_CONF = "testConf";

    private DefaultDependencyResolver dependencyResolver;
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
    private ResolveReport expectedResolveReport;
    private ResolveOptions expectedResolveOptions;

    @Before
    public void setUp() {
        report2ClasspathMock = context.mock(Report2Classpath.class);
        dependencyResolver = new DefaultDependencyResolver(report2ClasspathMock);
        ivyMock = context.mock(Ivy.class);
        expectedSettings = new IvySettings();
        expectedClasspath = WrapUtil.toList(new File(""));
        expectedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                DependenciesUtil.moduleRevisionId("org", "name", "1.0", new HashMap()));
        expectedResolveReport = new ResolveReport(expectedModuleDescriptor);
        expectedResolveOptions = new ResolveOptions();
        expectedResolveOptions.setConfs(WrapUtil.toArray(TEST_CONF));
        expectedResolveOptions.setOutputReport(false);
    }

    @Test
    public void testResolve() throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(ivyMock).resolve(with(equal(expectedModuleDescriptor)), with(reflectionEquals(expectedResolveOptions)));
                will(returnValue(expectedResolveReport));
                allowing(report2ClasspathMock).getClasspath(TEST_CONF, expectedResolveReport);
                will(returnValue(expectedClasspath));
            }
        });
        assertSame(expectedClasspath, dependencyResolver.resolve(TEST_CONF, ivyMock, expectedModuleDescriptor, true));
    }

    @Test(expected = GradleException.class)
    public void testResolveWithMissingDependenciesAndFailTrue() throws IOException, ParseException {
        final ResolveReport resolveReportMock = context.mock(ResolveReport.class);
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError(); will(returnValue(true));
                allowing(ivyMock).resolve(with(equal(expectedModuleDescriptor)), with(reflectionEquals(expectedResolveOptions)));
                will(returnValue(resolveReportMock));
            }
        });
        dependencyResolver.resolve(TEST_CONF, ivyMock, expectedModuleDescriptor, true);
    }

    @Test
    public void testResolveWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        final ResolveReport resolveReportMock = context.mock(ResolveReport.class);
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError(); will(returnValue(true));
                allowing(ivyMock).resolve(with(equal(expectedModuleDescriptor)), with(reflectionEquals(expectedResolveOptions)));
                will(returnValue(resolveReportMock));
                allowing(report2ClasspathMock).getClasspath(TEST_CONF, resolveReportMock);
                will(returnValue(expectedClasspath));
            }
        });
        assertSame(expectedClasspath, dependencyResolver.resolve(TEST_CONF, ivyMock, expectedModuleDescriptor, false));
    }


}
