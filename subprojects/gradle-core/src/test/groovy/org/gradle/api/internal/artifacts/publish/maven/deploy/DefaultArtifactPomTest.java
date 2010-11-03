/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPom;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPomTest {
    private DefaultArtifactPom artifactPom;
    private MavenPom testPom;
    private File expectedFile;
    private Artifact expectedArtifact;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        expectedFile = new File("somePath");
        expectedArtifact = createTestArtifact("someName");
        testPom = new DefaultMavenPom(context.mock(ConfigurationContainer.class), new DefaultConf2ScopeMappingContainer(),
                context.mock(PomDependenciesConverter.class), context.mock(FileResolver.class));
        artifactPom = new DefaultArtifactPom(testPom);
    }

    @Test
    public void pomWithMainArtifact() {
        artifactPom.addArtifact(expectedArtifact, expectedFile);

        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision());
    }

    @Test
    public void pomWithMainArtifactAndClassifierArtifacts() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), "javadoc", "zip");

        artifactPom.addArtifact(classifierArtifact, classifierFile);
        artifactPom.addArtifact(expectedArtifact, expectedFile);

        assertThat(artifactPom.getClassifiers(),
                hasItem(new ClassifierArtifact("javadoc", "sometype", new File("someFile"))));

        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision());
    }

    @Test
    public void pomWithClassifierArtifacts() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), "javadoc", "zip");

        artifactPom.addArtifact(classifierArtifact, classifierFile);

        assertThat(artifactPom.getClassifiers(),
                hasItem(new ClassifierArtifact("javadoc", "sometype", new File("someFile"))));
        checkPom(classifierArtifact.getModuleRevisionId().getOrganisation(),
                classifierArtifact.getName(), "jar",
                classifierArtifact.getModuleRevisionId().getRevision());
    }

    @Test
    public void pomWithMainArtifactAndMetadataArtifacts() {
        File metadataFile = new File("someMetadataFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), null, "sometype");

        artifactPom.addArtifact(classifierArtifact, metadataFile);
        artifactPom.addArtifact(expectedArtifact, expectedFile);

        assertThat(artifactPom.getClassifiers(),
                hasItem(new ClassifierArtifact(null, "sometype", metadataFile)));

        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(expectedArtifact.getModuleRevisionId().getOrganisation(), expectedArtifact.getName(),
                expectedArtifact.getType(), expectedArtifact.getModuleRevisionId().getRevision());
    }
    
    @Test(expected = InvalidUserDataException.class)
    public void addClassifierTwiceShouldThrowInvalidUserDataEx() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact(expectedArtifact.getName(), "javadoc");
        artifactPom.addArtifact(classifierArtifact, classifierFile);
        artifactPom.addArtifact(classifierArtifact, classifierFile);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addMainArtifactTwiceShouldThrowInvalidUserDataEx() {
        artifactPom.addArtifact(expectedArtifact, expectedFile);
        artifactPom.addArtifact(expectedArtifact, expectedFile);
    }

    @Test
    public void initWithCustomPomSettings() {
        testPom.setArtifactId(expectedArtifact.getName() + "X");
        testPom.setGroupId(expectedArtifact.getModuleRevisionId().getOrganisation() + "X");
        testPom.setVersion(expectedArtifact.getModuleRevisionId().getRevision() + "X");
        testPom.setPackaging(expectedArtifact.getType() + "X");
        artifactPom = new DefaultArtifactPom(testPom);
        artifactPom.addArtifact(expectedArtifact, expectedFile);
        assertEquals(expectedArtifact, artifactPom.getArtifact());
        assertEquals(expectedFile, artifactPom.getArtifactFile());
        checkPom(testPom.getGroupId(), testPom.getArtifactId(), testPom.getPackaging(), testPom.getVersion());
    }

    private void checkPom(String organisation, String name, String type, String revision) {
        assertEquals(organisation, testPom.getGroupId());
        assertEquals(name, testPom.getArtifactId());
        assertEquals(type, testPom.getPackaging());
        assertEquals(revision, testPom.getVersion());
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
        return createTestArtifact(name, classifier, "jar");
    }

    private Artifact createTestArtifact(String name, String classifier, String type) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (classifier != null) {
            extraAttributes.put(Dependency.CLASSIFIER, classifier);
        }
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, type, type, extraAttributes);
    }

    @Test
    public void writePom() {
        final MavenPom mavenPomMock = context.mock(MavenPom.class);
        DefaultArtifactPom artifactPom = new DefaultArtifactPom(mavenPomMock);
        final File somePomFile = new File(tmpDir.getDir(), "someDir/somePath");
        final Set<Configuration> configurations = WrapUtil.toSet(context.mock(Configuration.class));
        context.checking(new Expectations() {{
            one(mavenPomMock).writeTo(with(any(FileWriter.class)));       
        }});
        artifactPom.writePom(somePomFile);
        assertThat(somePomFile.getParentFile().isDirectory(), equalTo(true));
    }
}
