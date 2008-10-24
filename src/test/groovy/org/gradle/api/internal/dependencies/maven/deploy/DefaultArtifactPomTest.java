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
package org.gradle.api.internal.dependencies.maven.deploy;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.runner.RunWith;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.PublishFilter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.dependencies.maven.DefaultMavenPom;
import org.gradle.api.internal.dependencies.maven.PomFileWriter;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactPomTest {
    private DefaultArtifactPom artifactPom;
    private MavenPom testPom;
    private PublishFilter testFilter;

    private JUnit4Mockery context = new JUnit4Mockery();
    private static final String TEST_NAME = "name";

    @Before
    public void setUp() {
        testPom = new DefaultMavenPom(context.mock(PomFileWriter.class),
                new DefaultConf2ScopeMappingContainer(), new ArrayList<DependencyDescriptor>());
        testFilter = PublishFilter.ALWAYS_ACCEPT;
        artifactPom = new DefaultArtifactPom(TEST_NAME, testPom, testFilter);
    }

    @Test
    public void init() {
        assertEquals(TEST_NAME, artifactPom.getName());
        assertSame(testPom, artifactPom.getPom());
        assertSame(testFilter, artifactPom.getFilter());
    }

    @Test(expected = InvalidUserDataException.class)
    public void addMultipleArtifactsAcceptedByFilter() {
        artifactPom.addArtifact(createTestArtifact("name1"), new File("somePath"));
        artifactPom.addArtifact(createTestArtifact("name2"), new File("somePath2"));
    }

    @Test
    public void addArtifact() {
        File expectedFile = new File("somePath");
        final Artifact expectedArtifact = createTestArtifact("someName");
        artifactPom.addArtifact(expectedArtifact, expectedFile);
        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision(), expectedArtifact.getExtraAttribute(DependencyManager.CLASSIFIER));
    }

    @Test
    public void addArtifactWithCustomPomSettings() {
        File expectedFile = new File("somePath");
        final Artifact expectedArtifact = createTestArtifact("someName");
        testPom.setArtifactId(expectedArtifact.getName() + "X");
        testPom.setGroupId(expectedArtifact.getModuleRevisionId().getOrganisation() + "X");
        testPom.setVersion(expectedArtifact.getModuleRevisionId().getRevision() + "X");
        testPom.setPackaging(expectedArtifact.getType() + "X");
        testPom.setClassifier(expectedArtifact.getExtraAttribute(DependencyManager.CLASSIFIER) + "X");
        artifactPom.addArtifact(expectedArtifact, expectedFile);
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
    public void addArtifactWithSrcNull() {
        artifactPom.addArtifact(createTestArtifact("somename"), null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addArtifactWithArtifactNull() {
        artifactPom.addArtifact(null, new File("somepath"));
    }

    @Test
    public void addMultipleArtifactsWithOnlyOneAcceptedByFilter() {
        final File srcFile1 = new File("src1");
        final File srcFile2 = new File("src2");
        final File srcFile3 = new File("src3");
        PublishFilter fileFilter = new PublishFilter() {
            public boolean accept(Artifact artifact, File src) {
                return src.equals(srcFile2);
            }
        };
        artifactPom.setFilter(fileFilter);
        Artifact artifact1 = createTestArtifact("someName1");
        Artifact artifact2 = createTestArtifact("someName2");
        Artifact artifact3 = createTestArtifact("someName3");
        artifactPom.addArtifact(artifact1, srcFile1);
        artifactPom.addArtifact(artifact2, srcFile2);
        artifactPom.addArtifact(artifact3, srcFile3);
        assertEquals(artifact2, artifactPom.getArtifact());
        assertEquals(srcFile2, artifactPom.getArtifactFile());
    }

    private Artifact createTestArtifact(String name) {
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, "jar", "jar");
    }

    @Test
    public void toPom() {
        final File pomFile = new File("pomFile");
        artifactPom.setPom(testPom = context.mock(MavenPom.class));
        context.checking(new Expectations() {
            {
                one(testPom).toPomFile(pomFile);
            }
        });
        artifactPom.toPomFile(pomFile);
    }
}
