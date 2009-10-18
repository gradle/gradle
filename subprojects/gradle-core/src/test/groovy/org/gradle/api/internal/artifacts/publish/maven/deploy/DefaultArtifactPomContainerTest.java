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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider;
import org.gradle.api.internal.artifacts.publish.maven.PomFileWriter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactPomContainerTest {
    private static final File TEST_POM_DIR = new File("pomDir");

    private DefaultArtifactPomContainer artifactPomContainer;

    private MavenPomMetaInfoProvider metaInfoProviderMock;
    private PomFilterContainer pomFilterContainerMock;
    private PomFilter pomFilterMock;
    private PublishFilter publishFilterMock;
    private ArtifactPomFactory artifactPomFactoryMock;
    private ArtifactPom artifactPomMock;
    private MavenPom mavenPomMock;
    private MavenPom mavenTemplatePomMock;
    private PomFileWriter pomFileWriterMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    private Set<Configuration> testConfigurations;
    private File expectedFile;
    private File expectedPomFile;
    private Artifact expectedArtifact;
    private static final String POMFILTER_NAME = "somename";

    @Before
    public void setUp() {
        testConfigurations = new HashSet<Configuration>();
        expectedPomFile = new File(TEST_POM_DIR, "pom-" + POMFILTER_NAME + ".xml");
        expectedFile = new File("somePath");
        expectedArtifact = createTestArtifact("someName");
        pomFilterContainerMock = context.mock(PomFilterContainer.class);
        pomFilterMock = context.mock(PomFilter.class);
        pomFileWriterMock = context.mock(PomFileWriter.class);
        artifactPomMock = context.mock(ArtifactPom.class);
        artifactPomFactoryMock = context.mock(ArtifactPomFactory.class);
        publishFilterMock = context.mock(PublishFilter.class);
        mavenPomMock = context.mock(MavenPom.class);
        mavenTemplatePomMock = context.mock(MavenPom.class, "templatePom");
        metaInfoProviderMock = context.mock(MavenPomMetaInfoProvider.class);

        artifactPomContainer = new DefaultArtifactPomContainer(metaInfoProviderMock, pomFilterContainerMock,
                pomFileWriterMock, artifactPomFactoryMock);
    }

    @Test
    public void addArtifact() {
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getActivePomFilters(); will(returnValue(WrapUtil.toList(pomFilterMock)));
            allowing(pomFilterMock).getName(); will(returnValue(POMFILTER_NAME));
            allowing(pomFilterMock).getFilter(); will(returnValue(publishFilterMock));
            allowing(pomFilterMock).getPomTemplate(); will(returnValue(mavenTemplatePomMock));
            allowing(publishFilterMock).accept(expectedArtifact, expectedFile); will(returnValue(true));
            allowing(artifactPomFactoryMock).createArtifactPom(mavenTemplatePomMock); will(returnValue(artifactPomMock));
            one(artifactPomMock).addArtifact(expectedArtifact, expectedFile);
            allowing(artifactPomMock).getPom(); will(returnValue(mavenPomMock));
            allowing(artifactPomMock).getArtifactFile(); will(returnValue(expectedFile));
            allowing(artifactPomMock).getClassifiers(); will(returnValue(new HashSet<ClassifierArtifact>()));
            allowing(metaInfoProviderMock).getMavenPomDir(); will(returnValue(TEST_POM_DIR));
            one(pomFileWriterMock).write(with(same(mavenPomMock)), with(same(testConfigurations)), with(equal(expectedPomFile)));
        }});
        artifactPomContainer.addArtifact(expectedArtifact, expectedFile);
        Set<DeployableFilesInfo> deployableFilesInfos = artifactPomContainer.createDeployableFilesInfos(testConfigurations);
        assertEquals(1, deployableFilesInfos.size());
        assertEquals(expectedFile, deployableFilesInfos.iterator().next().getArtifactFile());
        assertEquals(expectedPomFile, deployableFilesInfos.iterator().next().getPomFile());
    }

    @Test
    public void addArtifactNotAcceptedByFilter() {
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getActivePomFilters(); will(returnValue(WrapUtil.toList(pomFilterMock)));
            allowing(pomFilterMock).getName(); will(returnValue(POMFILTER_NAME));
            allowing(pomFilterMock).getFilter(); will(returnValue(publishFilterMock));
            allowing(publishFilterMock).accept(expectedArtifact, expectedFile); will(returnValue(false));
        }});
        artifactPomContainer.addArtifact(expectedArtifact, expectedFile);
        assertTrue(artifactPomContainer.getArtifactPoms().isEmpty());
    }

    @Test(expected= InvalidUserDataException.class)
    public void addArtifactWithNullArtifact() {
        artifactPomContainer.addArtifact(null, expectedFile);
    }

    @Test(expected= InvalidUserDataException.class)
    public void addArtifactWithNullFile() {
        artifactPomContainer.addArtifact(expectedArtifact, null);
    }

    private Artifact createTestArtifact(String name) {
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, "jar", "jar");
    }
}
