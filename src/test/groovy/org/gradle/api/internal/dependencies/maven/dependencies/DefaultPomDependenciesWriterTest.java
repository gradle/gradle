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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import org.gradle.util.WrapUtil;
import static org.gradle.api.internal.dependencies.maven.PomWriter.NL;
import org.gradle.api.internal.dependencies.maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultPomDependenciesWriter;
import org.gradle.api.internal.dependencies.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.dependencies.maven.XmlHelper;
import org.gradle.api.internal.dependencies.maven.dependencies.PomDependenciesWriter;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultPomDependenciesWriterTest {
    DefaultPomDependenciesWriter dependenciesWriter;
    private StringWriter testStringWriter;
    private PomDependenciesConverter dependenciesConverterMock;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private MavenPom pomMock;
    private PrintWriter testPrintWriter;
    private List<DependencyDescriptor> testDependencies;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        testDependencies = new ArrayList<DependencyDescriptor>();
        dependenciesConverterMock = context.mock(PomDependenciesConverter.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        dependenciesWriter = new DefaultPomDependenciesWriter(dependenciesConverterMock);
        testStringWriter = new StringWriter();
        testPrintWriter = new PrintWriter(testStringWriter);
        pomMock = context.mock(MavenPom.class);
    }

    @Test
    public void init() {
        assertSame(dependenciesConverterMock, dependenciesWriter.getDependenciesConverter());
    }

    @Test
    public void convert() {
        final MavenDependency testMavenDependency = context.mock(MavenDependency.class);
        context.checking(new Expectations() {
            {
                one(testMavenDependency).write(testPrintWriter);
                one(dependenciesConverterMock).convert(pomMock, testDependencies);
                will(returnValue(WrapUtil.toList(testMavenDependency)));
            }
        });
        dependenciesWriter.convert(pomMock, testDependencies, testPrintWriter);
        assertEquals(XmlHelper.openTag(2, PomDependenciesWriter.DEPENDENCIES) + NL +
                XmlHelper.closeTag(2, PomDependenciesWriter.DEPENDENCIES) + NL, testStringWriter.toString());
    }

    @Test
    public void convertWithNoDependencies() {
        context.checking(new Expectations() {
            {
                one(dependenciesConverterMock).convert(pomMock, testDependencies);
                will(returnValue(new ArrayList()));
            }
        });
        dependenciesWriter.convert(pomMock, testDependencies, testPrintWriter);
        assertEquals("", testStringWriter.toString());
    }
}
