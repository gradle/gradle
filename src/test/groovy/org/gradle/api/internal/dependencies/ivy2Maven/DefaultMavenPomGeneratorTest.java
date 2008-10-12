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
package org.gradle.api.internal.dependencies.ivy2Maven;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;import static org.junit.Assert.assertSame;
import org.gradle.api.internal.dependencies.ivy2Maven.DefaultMavenPomGenerator;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.Conf2ScopeMappingContainer;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultMavenPomGeneratorTest {
    private static final String EXPECTED_PACKAGING = "something";
    private static final String EXPECTED_LICENSE_HEADER = "something";

    DefaultMavenPomGenerator mavenPomGenerator;
    PomModuleDescriptorFileWriter pomModuleDescriptorFileWriterMock;
    Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        pomModuleDescriptorFileWriterMock = context.mock(PomModuleDescriptorFileWriter.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        mavenPomGenerator = new DefaultMavenPomGenerator(pomModuleDescriptorFileWriterMock, conf2ScopeMappingContainerMock);
        mavenPomGenerator.setPackaging(EXPECTED_PACKAGING);
        mavenPomGenerator.setLicenseHeader(EXPECTED_LICENSE_HEADER);
    }

    @Test
    public void scopeMappings() {
        assertSame(conf2ScopeMappingContainerMock, mavenPomGenerator.getScopeMappings());
    }

    @Test
    public void setPackaging() {
        assertEquals(EXPECTED_PACKAGING, mavenPomGenerator.getPackaging());
    }

    @Test
    public void setLicensHeader() {
        assertEquals(EXPECTED_LICENSE_HEADER, mavenPomGenerator.getLicenseHeader());
    }
    
    @Test
    public void toPomFile() {
        final ModuleDescriptor moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        final File expectedPomFile = new File("somefile");
        context.checking(new Expectations() {{
            one(pomModuleDescriptorFileWriterMock).write(moduleDescriptorMock, EXPECTED_PACKAGING, EXPECTED_LICENSE_HEADER,
                    conf2ScopeMappingContainerMock, expectedPomFile);
        }});
        mavenPomGenerator.toPomFile(moduleDescriptorMock, expectedPomFile);
    }
}
