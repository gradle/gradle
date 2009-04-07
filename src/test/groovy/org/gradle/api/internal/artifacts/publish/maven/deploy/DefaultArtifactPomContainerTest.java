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
import org.gradle.api.internal.artifacts.publish.maven.PomFileWriter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactPomContainerTest {
    private static final File TEST_POM_DIR = new File("pomDir");

    private DefaultArtifactPomContainer artifactPomContainer;

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
        artifactPomContainer = new DefaultArtifactPomContainer(TEST_POM_DIR, pomFilterContainerMock,
                pomFileWriterMock, artifactPomFactoryMock);
    }

    @Test
    public void init() {
        assertEquals(TEST_POM_DIR, artifactPomContainer.getPomDir());
    }

    @Test
    public void addArtifact() {
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getActivePomFilters(); will(returnValue(WrapUtil.toList(pomFilterMock)));
            allowing(pomFilterMock).getName(); will(returnValue(POMFILTER_NAME));
            allowing(pomFilterMock).getFilter(); will(returnValue(publishFilterMock));
            allowing(pomFilterMock).getPomTemplate(); will(returnValue(mavenTemplatePomMock));
            allowing(publishFilterMock).accept(expectedArtifact, expectedFile); will(returnValue(true));
            allowing(artifactPomFactoryMock).createArtifactPom(mavenTemplatePomMock, expectedArtifact, expectedFile); will(returnValue(artifactPomMock));

            allowing(artifactPomMock).getPom(); will(returnValue(mavenPomMock));
            allowing(artifactPomMock).getArtifactFile(); will(returnValue(expectedFile));
            one(pomFileWriterMock).write(with(same(mavenPomMock)), with(same(testConfigurations)), with(equal(expectedPomFile)));
        }});
        artifactPomContainer.addArtifact(expectedArtifact, expectedFile);
        Map<File, File> files = artifactPomContainer.createDeployableUnits(testConfigurations);
        assertEquals(1, files.size());
        assertEquals(expectedFile, files.get(expectedPomFile));
    }

    @Test(expected = InvalidUserDataException.class)
    public void addArtifactWithMultipleArtifactsPerPom() {
        artifactPomContainer.getArtifactPoms().put(POMFILTER_NAME, context.mock(ArtifactPom.class, "firstOne"));
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getActivePomFilters(); will(returnValue(WrapUtil.toList(pomFilterMock)));
            allowing(pomFilterMock).getName(); will(returnValue(POMFILTER_NAME));
            allowing(pomFilterMock).getFilter(); will(returnValue(publishFilterMock));
            allowing(publishFilterMock).accept(expectedArtifact, expectedFile); will(returnValue(true));
        }});
        artifactPomContainer.addArtifact(expectedArtifact, expectedFile);
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
//
//    @Test
//    public void addArtifactWithOnlyDefaultArtifactPom() {
//        final ArtifactPom defaultPom = context.mock(ArtifactPom.class);
//        artifactPomContainer.setDefaultArtifactPom(defaultPom);
//        context.checking(new Expectations() {{
//            one(defaultPom).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
//        }});
//        artifactPomContainer.addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
//    }
//
//    @Test
//    public void addArtifactWithCustomArtifactPom() {
//        final ArtifactPom defaultPom = context.mock(ArtifactPom.class, "default");
//        final ArtifactPom customPom1 = context.mock(ArtifactPom.class, "pom1");
//        final ArtifactPom customPom2 = context.mock(ArtifactPom.class, "pom2");
//        artifactPomContainer.setDefaultArtifactPom(defaultPom);
//        context.checking(new Expectations() {{
//            allowing(customPom1).getName(); will(returnValue("customPom1"));
//            allowing(customPom2).getName(); will(returnValue("customPom2"));
//            one(customPom1).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
//            one(customPom2).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
//        }});
//        artifactPomContainer.addArtifactPom(customPom1);
//        artifactPomContainer.addArtifactPom(customPom2);
//        artifactPomContainer.addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
//    }
//
//    @Test
//    public void getArtifactPoms() {
//        final ArtifactPom defaultPom = context.mock(ArtifactPom.class, "default");
//        final ArtifactPom customPom = context.mock(ArtifactPom.class, "custom");
//        final String customPomName = "customPom";
//        context.checking(new Expectations() {{
//            allowing(customPom).getName(); will(returnValue(customPomName));
//        }});
//        artifactPomContainer.setDefaultArtifactPom(defaultPom);
//        artifactPomContainer.addArtifactPom(customPom);
//        assertSame(defaultPom, artifactPomContainer.getDefaultArtifactPom());
//        assertSame(customPom, artifactPomContainer.getArtifactPom(customPomName));
//    }
//
//    @Test
//    public void createDeployableUnitsWithNoArtifacts() {
//        assertEquals(0, artifactPomContainer.createDeployableUnits(testConfigurations).size());
//    }
//
//    @Test
//    public void createDeployableUnits() {
//        Map<File, File> expectedDeployableUnits = new HashMap<File, File>();
//        addPomArtifactFile(expectedDeployableUnits, true, "customPom1", "customPom2");
//        addPomArtifactFile(expectedDeployableUnits, false, "customPom3");
//        assertEquals(expectedDeployableUnits, artifactPomContainer.createDeployableUnits(testConfigurations));
//    }
//
//    private void addPomArtifactFile(Map<File, File> deployableUnits, final boolean addArtifactFile, String... names) {
//        for (String name : names) {
//            final File pomFile = new File(TEST_POM_DIR, "pom-" + name + ".xml");
//            final File artifactFile;
//            if (addArtifactFile) {
//                artifactFile = new File(name);
//                deployableUnits.put(pomFile, artifactFile);
//            } else {
//                artifactFile = null;
//            }
//            final ArtifactPom artifactPomMock = context.mock(ArtifactPom.class, name);
//            context.checking(new Expectations() {{
//                allowing(artifactPomMock).getArtifactFile(); will(returnValue(artifactFile));
//                allowing(artifactPomMock).getName(); will(returnValue(artifactFile == null ? pomFile.getName() : artifactFile.getName()));
//                if (artifactFile != null) {
//                    one(artifactPomMock).toPomFile(pomFile, testConfigurations);
//                }
//            }});
//            artifactPomContainer.addArtifactPom(artifactPomMock);
//        }
//    }

    private Artifact createTestArtifact(String name) {
        return new DefaultArtifact(ModuleRevisionId.newInstance("org", name, "1.0"), null, name, "jar", "jar");
    }
}
