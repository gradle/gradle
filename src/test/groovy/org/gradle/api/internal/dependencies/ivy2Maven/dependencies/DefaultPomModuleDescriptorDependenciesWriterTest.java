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
package org.gradle.api.internal.dependencies.ivy2Maven.dependencies;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.util.WrapUtil;
import static org.gradle.api.internal.dependencies.ivy2Maven.PomModuleDescriptorWriter.NL;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.DefaultPomModuleDescriptorDependenciesWriter;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.PomModuleDescriptorDependenciesConverter;
import org.gradle.api.internal.dependencies.ivy2Maven.XmlHelper;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.PomModuleDescriptorDependenciesWriter;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorDependenciesWriterTest {
    DefaultPomModuleDescriptorDependenciesWriter dependenciesWriter;
    private StringWriter testStringWriter;
    private PomModuleDescriptorDependenciesConverter dependenciesConverterMock;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private JUnit4Mockery context = new JUnit4Mockery();
    private DefaultModuleDescriptor moduleDescriptor;
    private PrintWriter testPrintWriter;

    @Before
    public void setUp() {
        dependenciesConverterMock = context.mock(PomModuleDescriptorDependenciesConverter.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        dependenciesWriter = new DefaultPomModuleDescriptorDependenciesWriter(dependenciesConverterMock);
        testStringWriter = new StringWriter();
        testPrintWriter = new PrintWriter(testStringWriter);
        moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "revision")
        );
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
                one(dependenciesConverterMock).convert(moduleDescriptor, true, conf2ScopeMappingContainerMock);
                will(returnValue(WrapUtil.toList(testMavenDependency)));
            }
        });
        dependenciesWriter.convert(moduleDescriptor, true, conf2ScopeMappingContainerMock,
                testPrintWriter);
        assertEquals(XmlHelper.openTag(2, PomModuleDescriptorDependenciesWriter.DEPENDENCIES) + NL +
                XmlHelper.closeTag(2, PomModuleDescriptorDependenciesWriter.DEPENDENCIES) + NL, testStringWriter.toString());
    }

    @Test
    public void convertWithNoDependencies() {
        context.checking(new Expectations() {
            {
                one(dependenciesConverterMock).convert(moduleDescriptor, true, conf2ScopeMappingContainerMock);
                will(returnValue(new ArrayList()));
            }
        });
        dependenciesWriter.convert(moduleDescriptor, true, conf2ScopeMappingContainerMock,
                testPrintWriter);
        assertEquals("", testStringWriter.toString());
    }
}
