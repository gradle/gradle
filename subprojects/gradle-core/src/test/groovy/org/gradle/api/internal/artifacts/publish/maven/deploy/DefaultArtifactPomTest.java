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
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

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
        artifactPom = new DefaultArtifactPom(testPom);
        artifactPom.addArtifact(expectedArtifact, expectedFile);
    }

    @Test
    public void addClassifier() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), "javadoc");
        artifactPom.addArtifact(classifierArtifact, classifierFile);
        assertThat(artifactPom.getClassifiers(),
                hasItem(new ClassifierArtifact("javadoc", "sometype", new File("someFile"))));
    }

    @Test(expected = InvalidUserDataException.class)
    public void addClassifierTwice_shouldThrowInvalidUserDataEx() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), "javadoc");
        artifactPom.addArtifact(classifierArtifact, classifierFile);
        artifactPom.addArtifact(classifierArtifact, classifierFile);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addMainArtifactTwice_shouldThrowInvalidUserDataEx() {
        artifactPom.addArtifact(expectedArtifact, expectedFile);
    }

    @Test
    public void init() {
        assertSame(testPom, artifactPom.getPom());
        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision(), null);
    }

    @Test
    public void initWithCustomPomSettings() {
        testPom.setArtifactId(expectedArtifact.getName() + "X");
        testPom.setGroupId(expectedArtifact.getModuleRevisionId().getOrganisation() + "X");
        testPom.setVersion(expectedArtifact.getModuleRevisionId().getRevision() + "X");
        testPom.setPackaging(expectedArtifact.getType() + "X");
        testPom.setClassifier("X");
        artifactPom = new DefaultArtifactPom(testPom);
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
    public void addArtifactWithArtifactSrcNull() {
        new DefaultArtifactPom(testPom).addArtifact(expectedArtifact, null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addArtifactWithArtifactNull() {
        new DefaultArtifactPom(testPom).addArtifact(null, expectedFile);
    }
    
    private Artifact createTestArtifact(String name) {
        return createTestArtifact(name, null);
    }

    private Artifact createTestArtifact(String name, String classifier) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (classifier != null) {
            extraAttributes.put(Dependency.CLASSIFIER, classifier);
        }
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, "jar", "jar", extraAttributes);
    }
}
