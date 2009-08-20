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
package org.gradle.api.internal.artifacts.publish.maven.deploy;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPom;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPomTest {
    private DefaultArtifactPom artifactPom;
    private MavenPom testPom;
    private File expectedFile;
    private Artifact expectedArtifact;

    @Before
    public void setUp() {
        expectedFile = new File("somePath");
        expectedArtifact = createTestArtifact("someName");
        testPom = new DefaultMavenPom(new DefaultConf2ScopeMappingContainer());
        artifactPom = new DefaultArtifactPom(testPom, expectedArtifact, expectedFile);
    }

    @Test
    public void init() {
        assertSame(testPom, artifactPom.getPom());
        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision(), expectedArtifact.getExtraAttribute(Dependency.CLASSIFIER));
    }

    @Test
    public void initWithCustomPomSettings() {
        testPom.setArtifactId(expectedArtifact.getName() + "X");
        testPom.setGroupId(expectedArtifact.getModuleRevisionId().getOrganisation() + "X");
        testPom.setVersion(expectedArtifact.getModuleRevisionId().getRevision() + "X");
        testPom.setPackaging(expectedArtifact.getType() + "X");
        testPom.setClassifier(expectedArtifact.getExtraAttribute(Dependency.CLASSIFIER) + "X");
        artifactPom = new DefaultArtifactPom(testPom, expectedArtifact, expectedFile);
        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(testPom.getGroupId(), testPom.getArtifactId(), testPom.getPackaging(), testPom.getVersion(), testPom.getClassifier());
    }

    private void checkPom(String organisation, String name, String type, String revision, String classifier) {
        assertEquals(organisation, testPom.getGroupId());
        assertEquals(name, testPom.getArtifactId());
        assertEquals(type, testPom.getPackaging());
        assertEquals(revision, testPom.getVersion());
        assertEquals(classifier, testPom.getClassifier());
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithArtifactSrcNull() {
        new DefaultArtifactPom(testPom, expectedArtifact, null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithArtifactNull() {
        new DefaultArtifactPom(testPom, null, expectedFile);
    }

//    @Test
//    public void addMultipleArtifactsWithOnlyOneAcceptedByFilter() {
//        final File srcFile1 = new File("src1");
//        final File srcFile2 = new File("src2");
//        final File srcFile3 = new File("src3");
//        PublishFilter fileFilter = new PublishFilter() {
//            public boolean accept(Artifact artifact, File src) {
//                return src.equals(srcFile2);
//            }
//        };
//        artifactPom.setPomFilter(fileFilter);
//        Artifact artifact1 = createTestArtifact("someName1");
//        Artifact artifact2 = createTestArtifact("someName2");
//        Artifact artifact3 = createTestArtifact("someName3");
//        artifactPom.addArtifact(artifact1, srcFile1);
//        artifactPom.addArtifact(artifact2, srcFile2);
//        artifactPom.addArtifact(artifact3, srcFile3);
//        assertEquals(artifact2, artifactPom.getArtifact());
//        assertEquals(srcFile2, artifactPom.getArtifactFile());
//    }

    private Artifact createTestArtifact(String name) {
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, "jar", "jar");
    }
}
