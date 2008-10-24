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
package org.gradle.api.internal.dependencies.maven;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.dependencies.maven.dependencies.PomModuleDescriptorDependenciesWriter;
import org.gradle.api.dependencies.maven.MavenPom;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultPomModuleDescriptorWriterTest {
    private DefaultPomModuleDescriptorWriter pomModuleDescriptorWriter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;

    private PomModuleDescriptorHeaderWriter headerWriterMock;
    private PomModuleDescriptorModuleIdWriter moduleIdWriterMock;
    private PomModuleDescriptorDependenciesWriter dependenciesWriterMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        headerWriterMock = context.mock(PomModuleDescriptorHeaderWriter.class);
        moduleIdWriterMock = context.mock(PomModuleDescriptorModuleIdWriter.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        dependenciesWriterMock = context.mock(PomModuleDescriptorDependenciesWriter.class);
        pomModuleDescriptorWriter = new DefaultPomModuleDescriptorWriter(headerWriterMock, moduleIdWriterMock,
                dependenciesWriterMock);
    }

    @Test
    public void init() {
        assertSame(headerWriterMock, pomModuleDescriptorWriter.getHeaderWriter());
        assertSame(moduleIdWriterMock, pomModuleDescriptorWriter.getModuleIdWriter());
        assertSame(dependenciesWriterMock, pomModuleDescriptorWriter.getDependenciesWriter());
    }

    @Test
    public void convert() {
        StringWriter stringWriter = new StringWriter();
        final PrintWriter testPrintWriter = new PrintWriter(stringWriter);
        final MavenPom pomMock = context.mock(MavenPom.class);
        final String testLicenseText = "licenseText";
        context.checking(new Expectations() {
            {
                allowing(pomMock).getLicenseHeader(); will(returnValue(testLicenseText));
                one(headerWriterMock).convert(testLicenseText, testPrintWriter);
                one(moduleIdWriterMock).convert(pomMock, testPrintWriter);
                one(dependenciesWriterMock).convert(with(same(pomMock)),
                        with(same(testPrintWriter)));
            }
        });
        pomModuleDescriptorWriter.convert(pomMock, testPrintWriter);
        assertEquals(String.format("</" + PomModuleDescriptorWriter.ROOT_ELEMENT_NAME + ">%n"), stringWriter.toString());
    }
}
