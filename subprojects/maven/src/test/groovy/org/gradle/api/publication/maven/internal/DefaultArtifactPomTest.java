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
package org.gradle.api.publication.maven.internal;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DefaultArtifactPomTest {
    private DefaultArtifactPom artifactPom;
    private MavenPom testPom;

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        testPom = new DefaultMavenPom(context.mock(ConfigurationContainer.class), context.mock(Conf2ScopeMappingContainer.class),
                context.mock(PomDependenciesConverter.class), context.mock(FileResolver.class));
        artifactPom = new DefaultArtifactPom(testPom);
    }

    @Test
    public void pomWithMainArtifact() {
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging");
        File mainFile = new File("someFile");

        artifactPom.addArtifact(mainArtifact, mainFile);

        assertThat(artifactPom.getArtifact().getName(), equalTo("someName"));
        assertThat(artifactPom.getArtifact().getExtension(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getType(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getClassifier(), nullValue());
        assertThat(artifactPom.getArtifact().getFile(), equalTo(mainFile));

        assertThat(artifactPom.getPom().getGroupId(), equalTo("org"));
        assertThat(artifactPom.getPom().getArtifactId(), equalTo("someName"));
        assertThat(artifactPom.getPom().getVersion(), equalTo("1.0"));
        assertThat(artifactPom.getPom().getPackaging(), equalTo("mainPackaging"));
    }

    @Test
    public void pomWithMainArtifactAndClassifierArtifacts() {
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging");
        File mainFile = new File("someFile");
        Artifact classifierArtifact = createTestArtifact("otherName", "javadoc", "zip");
        File classifierFile = new File("someClassifierFile");

        artifactPom.addArtifact(mainArtifact, mainFile);
        artifactPom.addArtifact(classifierArtifact, classifierFile);

        assertThat(artifactPom.getArtifact().getName(), equalTo("someName"));
        assertThat(artifactPom.getArtifact().getExtension(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getType(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getClassifier(), nullValue());
        assertThat(artifactPom.getArtifact().getFile(), equalTo(mainFile));

        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts());
        assertThat(artifact.getName(), equalTo("someName"));
        assertThat(artifact.getExtension(), equalTo("zip"));
        assertThat(artifact.getType(), equalTo("zip"));
        assertThat(artifact.getClassifier(), equalTo("javadoc"));
        assertThat(artifact.getFile(), equalTo(classifierFile));

        assertThat(artifactPom.getPom().getGroupId(), equalTo("org"));
        assertThat(artifactPom.getPom().getArtifactId(), equalTo("someName"));
        assertThat(artifactPom.getPom().getVersion(), equalTo("1.0"));
        assertThat(artifactPom.getPom().getPackaging(), equalTo("mainPackaging"));
    }

    @Test
    public void pomWithClassifierArtifactsOnly() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact("someName", "javadoc", "zip");

        artifactPom.addArtifact(classifierArtifact, classifierFile);

        assertThat(artifactPom.getArtifact(), nullValue());

        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts());
        assertThat(artifact.getName(), equalTo("someName"));
        assertThat(artifact.getExtension(), equalTo("zip"));
        assertThat(artifact.getType(), equalTo("zip"));
        assertThat(artifact.getClassifier(), equalTo("javadoc"));
        assertThat(artifact.getFile(), equalTo(classifierFile));

        assertThat(artifactPom.getPom().getGroupId(), equalTo("org"));
        assertThat(artifactPom.getPom().getArtifactId(), equalTo("someName"));
        assertThat(artifactPom.getPom().getVersion(), equalTo("1.0"));
        assertThat(artifactPom.getPom().getPackaging(), equalTo("jar"));
    }

    @Test
    public void pomWithMainArtifactAndMetadataArtifacts() {
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging");
        File mainFile = new File("someFile");
        File metadataFile = new File("someMetadataFile");
        Artifact metadataArtifact = createTestArtifact("otherName", null, "sometype");

        artifactPom.addArtifact(mainArtifact, mainFile);
        artifactPom.addArtifact(metadataArtifact, metadataFile);

        assertThat(artifactPom.getArtifact().getName(), equalTo("someName"));
        assertThat(artifactPom.getArtifact().getExtension(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getType(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getClassifier(), nullValue());
        assertThat(artifactPom.getArtifact().getFile(), equalTo(mainFile));

        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts());
        assertThat(artifact.getName(), equalTo("someName"));
        assertThat(artifact.getExtension(), equalTo("sometype"));
        assertThat(artifact.getType(), equalTo("sometype"));
        assertThat(artifact.getClassifier(), nullValue());
        assertThat(artifact.getFile(), equalTo(metadataFile));

        assertThat(artifactPom.getPom().getGroupId(), equalTo("org"));
        assertThat(artifactPom.getPom().getArtifactId(), equalTo("someName"));
        assertThat(artifactPom.getPom().getVersion(), equalTo("1.0"));
        assertThat(artifactPom.getPom().getPackaging(), equalTo("mainPackaging"));
    }
    
    @Test(expected = InvalidUserDataException.class)
    public void addClassifierTwiceShouldThrowInvalidUserDataEx() {
        File classifierFile = new File("someClassifierFile");
        Artifact classifierArtifact = createTestArtifact("someName", "javadoc");
        artifactPom.addArtifact(classifierArtifact, classifierFile);
        artifactPom.addArtifact(classifierArtifact, classifierFile);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addMainArtifactTwiceShouldThrowInvalidUserDataEx() {
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging");
        File mainFile = new File("someFile");
        artifactPom.addArtifact(mainArtifact, mainFile);
        artifactPom.addArtifact(mainArtifact, mainFile);
    }

    @Test
    public void cannotAddMultipleArtifactsWithTheSameTypeAndClassifier() {

        // No classifier
        Artifact mainArtifact = createTestArtifact("someName", null);
        artifactPom.addArtifact(mainArtifact, new File("someFile"));

        assertIsDuplicate(mainArtifact, new File("someFile"));
        assertIsDuplicate(mainArtifact, new File("otherFile"));
        assertIsDuplicate(createTestArtifact("otherName", null), new File("otherFile"));

        // Classifier
        Artifact classifierArtifact = createTestArtifact("someName", "classifier");
        artifactPom.addArtifact(classifierArtifact, new File("classifierFile"));

        assertIsDuplicate(classifierArtifact, new File("someFile"));
        assertIsDuplicate(classifierArtifact, new File("otherFile"));
        assertIsDuplicate(createTestArtifact("otherName", "classifier"), new File("otherFile"));
    }

    private void assertIsDuplicate(Artifact artifact, File file) {
        try {
            artifactPom.addArtifact(artifact, file);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), startsWith("A POM cannot have multiple artifacts with the same type and classifier."));
        }
    }

    @Test
    public void initWithCustomPomSettings() {
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging");
        File mainFile = new File("someFile");

        testPom.setArtifactId("customArtifactId");
        testPom.setGroupId("customGroupId");
        testPom.setVersion("customVersion");
        testPom.setPackaging("customPackaging");

        artifactPom.addArtifact(mainArtifact, mainFile);

        assertThat(artifactPom.getArtifact().getName(), equalTo("customArtifactId"));
        assertThat(artifactPom.getArtifact().getExtension(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getType(), equalTo("mainPackaging"));
        assertThat(artifactPom.getArtifact().getClassifier(), nullValue());
        assertThat(artifactPom.getArtifact().getFile(), equalTo(mainFile));

        assertThat(artifactPom.getPom().getGroupId(), equalTo("customGroupId"));
        assertThat(artifactPom.getPom().getArtifactId(), equalTo("customArtifactId"));
        assertThat(artifactPom.getPom().getVersion(), equalTo("customVersion"));
        assertThat(artifactPom.getPom().getPackaging(), equalTo("mainPackaging"));
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
        final File somePomFile = new File(tmpDir.getTestDirectory(), "someDir/somePath");
        context.checking(new Expectations() {{
            allowing(mavenPomMock).getArtifactId();
            will(returnValue("artifactId"));
            one(mavenPomMock).writeTo(with(any(FileOutputStream.class)));
        }});

        PublishArtifact artifact = artifactPom.writePom(somePomFile);

        assertThat(artifact.getName(), equalTo("artifactId"));
        assertThat(artifact.getType(), equalTo("pom"));
        assertThat(artifact.getExtension(), equalTo("pom"));
        assertThat(artifact.getClassifier(), nullValue());
        assertThat(artifact.getFile(), equalTo(somePomFile));
    }

    private <T> T singleItem(Iterable<? extends T> collection) {
        List<T> elements = newArrayList(collection);
        assertThat(elements.size(), equalTo(1));
        return elements.get(0);
    }
}
