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
package org.gradle.api.internal.artifacts.publish.maven;

import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
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

    @Before
    public void setUp() {
        conf2ScopeMappingContainerMock = new DefaultConf2ScopeMappingContainer();
        mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock);
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
    public void copy() {
        DefaultConf2ScopeMappingContainer expectedScopeMappings = new DefaultConf2ScopeMappingContainer();
        DefaultMavenPom sourcePom = createTestPom(expectedScopeMappings);
        DefaultMavenPom targetPom = (DefaultMavenPom) sourcePom.copy();
        assertEquals(sourcePom.getArtifactId(), targetPom.getArtifactId());
        assertEquals(sourcePom.getClassifier(), targetPom.getClassifier());
        assertEquals(sourcePom.getGroupId(), targetPom.getGroupId());
        assertEquals(sourcePom.getLicenseHeader(), targetPom.getLicenseHeader());
        assertEquals(sourcePom.getPackaging(), targetPom.getPackaging());
        assertEquals(sourcePom.getVersion(), targetPom.getVersion());

    }

    private DefaultMavenPom createTestPom(DefaultConf2ScopeMappingContainer expectedScopeMappings) {
        DefaultMavenPom sourcePom = new DefaultMavenPom(expectedScopeMappings);
        sourcePom.setArtifactId("aid");
        sourcePom.setGroupId("gid");
        sourcePom.setVersion("vrs");
        sourcePom.setPackaging("pkg");
        sourcePom.setClassifier("cls");
        sourcePom.setLicenseHeader("lcs");
        return sourcePom;
    }
}
