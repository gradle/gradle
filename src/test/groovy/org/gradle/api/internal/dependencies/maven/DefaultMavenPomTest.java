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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;import static org.junit.Assert.assertSame;
import org.gradle.api.internal.dependencies.maven.DefaultMavenPom;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultMavenPomTest {
    private static final String EXPECTED_PACKAGING = "something";
    private static final String EXPECTED_LICENSE_HEADER = "licence";
    private static final String EXPECTED_GROUP_ID = "someGroup";
    private static final String EXPECTED_ARTIFACT_ID = "artifactId";
    private static final String EXPECTED_VERSION = "version";
    private static final String EXPECTED_CLASSIFIER = "classifier";

    DefaultMavenPom mavenPom;
    PomFileWriter pomFileWriterMock;
    Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    private List<DependencyDescriptor> testDependencies;

    @Before
    public void setUp() {
        pomFileWriterMock = context.mock(PomFileWriter.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        mavenPom = new DefaultMavenPom(pomFileWriterMock, conf2ScopeMappingContainerMock);
        mavenPom.setPackaging(EXPECTED_PACKAGING);
        mavenPom.setLicenseHeader(EXPECTED_LICENSE_HEADER);
        mavenPom.setGroupId(EXPECTED_GROUP_ID);
        mavenPom.setArtifactId(EXPECTED_ARTIFACT_ID);
        mavenPom.setVersion(EXPECTED_VERSION);
        mavenPom.setClassifier(EXPECTED_CLASSIFIER);
    }

    @Test
    public void initAndSetter() {
        assertSame(conf2ScopeMappingContainerMock, mavenPom.getScopeMappings());
        assertEquals(EXPECTED_PACKAGING, mavenPom.getPackaging());
        assertEquals(EXPECTED_ARTIFACT_ID, mavenPom.getArtifactId());
        assertEquals(EXPECTED_CLASSIFIER, mavenPom.getClassifier());
        assertEquals(EXPECTED_GROUP_ID, mavenPom.getGroupId());
        assertEquals(EXPECTED_LICENSE_HEADER, mavenPom.getLicenseHeader());
        assertEquals(EXPECTED_PACKAGING, mavenPom.getPackaging());
        assertEquals(EXPECTED_VERSION, mavenPom.getVersion());
    }

    @Test
    public void setLicensHeader() {
        assertEquals(EXPECTED_LICENSE_HEADER, mavenPom.getLicenseHeader());
    }
    
    @Test
    public void toPomFile() {
        final File expectedPomFile = new File("somefile");
        context.checking(new Expectations() {{
            one(pomFileWriterMock).write(mavenPom, testDependencies, expectedPomFile);
        }});
        mavenPom.toPomFile(expectedPomFile, testDependencies);
    }
}
