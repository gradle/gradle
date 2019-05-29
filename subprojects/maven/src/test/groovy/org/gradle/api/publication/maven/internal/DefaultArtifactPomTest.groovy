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
package org.gradle.api.publication.maven.internal

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.publication.maven.internal.pom.DefaultMavenPom
import org.gradle.api.publication.maven.internal.pom.PomDependenciesConverter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static com.google.common.collect.Lists.newArrayList
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.startsWith
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class DefaultArtifactPomTest extends Specification {
    private DefaultArtifactPom artifactPom
    private MavenPom testPom

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    
    def setup() {
        testPom = new DefaultMavenPom(Mock(ConfigurationContainer), Mock(Conf2ScopeMappingContainer),
                Mock(PomDependenciesConverter), Mock(FileResolver))
        artifactPom = new DefaultArtifactPom(testPom)
    }

    def pomWithMainArtifact() {
        when:
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging")
        File mainFile = new File("someFile")

        artifactPom.addArtifact(mainArtifact, mainFile)

        then:
        artifactPom.getArtifact().getName() == "someName"
        artifactPom.getArtifact().getExtension() == "mainPackaging"
        artifactPom.getArtifact().getType() == "mainPackaging"
        artifactPom.getArtifact().getClassifier() == null
        artifactPom.getArtifact().getFile() == mainFile

        artifactPom.getPom().getGroupId() == "org"
        artifactPom.getPom().getArtifactId() == "someName"
        artifactPom.getPom().getVersion() == "1.0"
        artifactPom.getPom().getPackaging() == "mainPackaging"
    }

    def pomWithMainArtifactAndClassifierArtifacts() {
        when:
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging")
        File mainFile = new File("someFile")
        Artifact classifierArtifact = createTestArtifact("otherName", "javadoc", "zip")
        File classifierFile = new File("someClassifierFile")

        artifactPom.addArtifact(mainArtifact, mainFile)
        artifactPom.addArtifact(classifierArtifact, classifierFile)
        
        then:
        artifactPom.getArtifact().getName() == "someName"
        artifactPom.getArtifact().getExtension() == "mainPackaging"
        artifactPom.getArtifact().getType() == "mainPackaging"
        artifactPom.getArtifact().getClassifier() == null
        artifactPom.getArtifact().getFile() == mainFile

        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts())
        artifact.getName() == "someName"
        artifact.getExtension() == "zip"
        artifact.getType() == "zip"
        artifact.getClassifier() == "javadoc"
        artifact.getFile() == classifierFile

        artifactPom.getPom().getGroupId() == "org"
        artifactPom.getPom().getArtifactId() == "someName"
        artifactPom.getPom().getVersion() == "1.0"
        artifactPom.getPom().getPackaging() == "mainPackaging"
    }

    def pomWithClassifierArtifactsOnly() {
        when:
        File classifierFile = new File("someClassifierFile")
        Artifact classifierArtifact = createTestArtifact("someName", "javadoc", "zip")

        artifactPom.addArtifact(classifierArtifact, classifierFile)
        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts())

        then:
        artifactPom.getArtifact() == null

        artifact.getName() == "someName"
        artifact.getExtension() == "zip"
        artifact.getType() == "zip"
        artifact.getClassifier() == "javadoc"
        artifact.getFile() == classifierFile

        artifactPom.getPom().getGroupId() == "org"
        artifactPom.getPom().getArtifactId() == "someName"
        artifactPom.getPom().getVersion() == "1.0"
        artifactPom.getPom().getPackaging() == "jar"
    }

    def pomWithMainArtifactAndMetadataArtifacts() {
        when:
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging")
        File mainFile = new File("someFile")
        File metadataFile = new File("someMetadataFile")
        Artifact metadataArtifact = createTestArtifact("otherName", null, "sometype")

        artifactPom.addArtifact(mainArtifact, mainFile)
        artifactPom.addArtifact(metadataArtifact, metadataFile)

        then:
        artifactPom.getArtifact().getName() == "someName"
        artifactPom.getArtifact().getExtension() == "mainPackaging"
        artifactPom.getArtifact().getType() == "mainPackaging"
        artifactPom.getArtifact().getClassifier() == null
        artifactPom.getArtifact().getFile() == mainFile

        PublishArtifact artifact = singleItem(artifactPom.getAttachedArtifacts())
        artifact.getName() == "someName"
        artifact.getExtension() == "sometype"
        artifact.getType() == "sometype"
        artifact.getClassifier() == null
        artifact.getFile() == metadataFile

        artifactPom.getPom().getGroupId() == "org"
        artifactPom.getPom().getArtifactId() == "someName"
        artifactPom.getPom().getVersion() == "1.0"
        artifactPom.getPom().getPackaging() == "mainPackaging"
    }

    def addClassifierTwiceShouldThrowInvalidUserDataEx() {
        given:
        File classifierFile = new File("someClassifierFile")
        Artifact classifierArtifact = createTestArtifact("someName", "javadoc")
        artifactPom.addArtifact(classifierArtifact, classifierFile)

        when:
        artifactPom.addArtifact(classifierArtifact, classifierFile)

        then:
        thrown(InvalidUserDataException)
    }

    def addMainArtifactTwiceShouldThrowInvalidUserDataEx() {
        given:
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging")
        File mainFile = new File("someFile")
        artifactPom.addArtifact(mainArtifact, mainFile)

        when:
        artifactPom.addArtifact(mainArtifact, mainFile)

        then:
        thrown(InvalidUserDataException)
    }

    def cannotAddMultipleArtifactsWithTheSameTypeAndClassifier() {
        when:
        // No classifier
        Artifact mainArtifact = createTestArtifact("someName", null)
        artifactPom.addArtifact(mainArtifact, new File("someFile"))

        then:
        assertIsDuplicate(mainArtifact, new File("someFile"))
        assertIsDuplicate(mainArtifact, new File("otherFile"))
        assertIsDuplicate(createTestArtifact("otherName", null), new File("otherFile"))

        when:
        // Classifier
        Artifact classifierArtifact = createTestArtifact("someName", "classifier")
        artifactPom.addArtifact(classifierArtifact, new File("classifierFile"))

        then:
        assertIsDuplicate(classifierArtifact, new File("someFile"))
        assertIsDuplicate(classifierArtifact, new File("otherFile"))
        assertIsDuplicate(createTestArtifact("otherName", "classifier"), new File("otherFile"))
    }

    private void assertIsDuplicate(Artifact artifact, File file) {
        try {
            artifactPom.addArtifact(artifact, file)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), startsWith("A POM cannot have multiple artifacts with the same type and classifier."))
        }
    }

    def initWithCustomPomSettings() {
        when:
        Artifact mainArtifact = createTestArtifact("someName", null, "mainPackaging")
        File mainFile = new File("someFile")

        testPom.setArtifactId("customArtifactId")
        testPom.setGroupId("customGroupId")
        testPom.setVersion("customVersion")
        testPom.setPackaging("customPackaging")

        artifactPom.addArtifact(mainArtifact, mainFile)

        then:
        artifactPom.getArtifact().getName() == "customArtifactId"
        artifactPom.getArtifact().getExtension() == "mainPackaging"
        artifactPom.getArtifact().getType() == "mainPackaging"
        artifactPom.getArtifact().getClassifier() == null
        artifactPom.getArtifact().getFile() == mainFile

        artifactPom.getPom().getGroupId() == "customGroupId"
        artifactPom.getPom().getArtifactId() == "customArtifactId"
        artifactPom.getPom().getVersion() == "customVersion"
        artifactPom.getPom().getPackaging() == "mainPackaging"
    }

    private Artifact createTestArtifact(String name, String classifier) {
        return createTestArtifact(name, classifier, "jar")
    }

    private Artifact createTestArtifact(String name, String classifier, String type) {
        Map<String, String> extraAttributes = new HashMap<String, String>()
        if (classifier != null) {
            extraAttributes.put(Dependency.CLASSIFIER, classifier)
        }
        return new DefaultArtifact(IvyUtil.createModuleRevisionId("org", name, "1.0"), null, name, type, type, extraAttributes)
    }

    def writePom() {
        final MavenPom mavenPomMock = Mock() {
            getArtifactId() >> "artifactId"
        }
        DefaultArtifactPom artifactPom = new DefaultArtifactPom(mavenPomMock)
        final File somePomFile = new File(tmpDir.getTestDirectory(), "someDir/somePath")

        when:
        PublishArtifact artifact = artifactPom.writePom(somePomFile)

        then:
        artifact.getName() == "artifactId"
        artifact.getType() == "pom"
        artifact.getExtension() == "pom"
        artifact.getClassifier() == null
        artifact.getFile() == somePomFile

        and:
        1 * mavenPomMock.writeTo(_)
    }

    private <T> T singleItem(Iterable<? extends T> collection) {
        List<T> elements = newArrayList(collection)
        assertThat(elements.size(), equalTo(1))
        return elements.get(0)
    }
}
