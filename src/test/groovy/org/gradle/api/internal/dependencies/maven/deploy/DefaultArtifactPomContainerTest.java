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
import org.junit.runner.RunWith;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import org.gradle.api.InvalidUserDataException;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactPomContainerTest {
    private static final File TEST_POM_DIR = new File("pomDir");
    private static final File TEST_JAR_FILE = new File("somejar.jar");
    private static final Artifact TEST_ARTIFACT = new DefaultArtifact(ModuleRevisionId.newInstance("org", "name", "1.0"), null, "name", "jar", "jar");

    private DefaultArtifactPomContainer artifactPomContainer;

    private JUnit4Mockery context = new JUnit4Mockery();

    private List<DependencyDescriptor> testDependencies;

    @Before
    public void setUp() {
        artifactPomContainer = new DefaultArtifactPomContainer(TEST_POM_DIR);
    }

    @Test
    public void init() {
        assertEquals(TEST_POM_DIR, artifactPomContainer.getPomDir());
    }

    @Test
    public void addArtifactPom() {
        testDependencies = new ArrayList<DependencyDescriptor>();
        final ArtifactPom artifactPom = context.mock(ArtifactPom.class);
        final String pomName = "pomName";
        context.checking(new Expectations() {{
            allowing(artifactPom).getName(); will(returnValue(pomName));
        }});
        artifactPomContainer.addArtifactPom(artifactPom);
        assertSame(artifactPom, artifactPomContainer.getArtifactPom(pomName));
    }

    @Test(expected= InvalidUserDataException.class)
    public void addArtifactPomWithNull() {
        artifactPomContainer.addArtifactPom(null);
    }

    @Test
    public void addArtifactWithOnlyDefaultArtifactPom() {
        final ArtifactPom defaultPom = context.mock(ArtifactPom.class);
        artifactPomContainer.setDefaultArtifactPom(defaultPom);
        context.checking(new Expectations() {{
            one(defaultPom).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
        }});
        artifactPomContainer.addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
    }

    @Test
    public void addArtifactWithCustomArtifactPom() {
        final ArtifactPom defaultPom = context.mock(ArtifactPom.class, "default");
        final ArtifactPom customPom1 = context.mock(ArtifactPom.class, "pom1");
        final ArtifactPom customPom2 = context.mock(ArtifactPom.class, "pom2");
        artifactPomContainer.setDefaultArtifactPom(defaultPom);
        context.checking(new Expectations() {{
            allowing(customPom1).getName(); will(returnValue("customPom1"));
            allowing(customPom2).getName(); will(returnValue("customPom2"));
            one(customPom1).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
            one(customPom2).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
        }});
        artifactPomContainer.addArtifactPom(customPom1);
        artifactPomContainer.addArtifactPom(customPom2);
        artifactPomContainer.addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
    }

    @Test
    public void getArtifactPoms() {
        final ArtifactPom defaultPom = context.mock(ArtifactPom.class, "default");
        final ArtifactPom customPom = context.mock(ArtifactPom.class, "custom");
        final String customPomName = "customPom";
        context.checking(new Expectations() {{
            allowing(customPom).getName(); will(returnValue(customPomName));
        }});
        artifactPomContainer.setDefaultArtifactPom(defaultPom);
        artifactPomContainer.addArtifactPom(customPom);
        assertSame(defaultPom, artifactPomContainer.getDefaultArtifactPom());
        assertSame(customPom, artifactPomContainer.getArtifactPom(customPomName));
    }

    @Test
    public void createDeployableUnitsWithNoArtifacts() {
        assertEquals(0, artifactPomContainer.createDeployableUnits(testDependencies).size());   
    }

    @Test
    public void createDeployableUnits() {
        Map<File, File> expectedDeployableUnits = new HashMap<File, File>();
        addPomArtifactFile(expectedDeployableUnits, true, "customPom1", "customPom2");
        addPomArtifactFile(expectedDeployableUnits, false, "customPom3");
        assertEquals(expectedDeployableUnits, artifactPomContainer.createDeployableUnits(testDependencies));
    }

    private void addPomArtifactFile(Map<File, File> deployableUnits, final boolean addArtifactFile, String... names) {
        for (String name : names) {
            final File pomFile = new File(TEST_POM_DIR, "pom-" + name + ".xml");
            final File artifactFile;
            if (addArtifactFile) {
                artifactFile = new File(name);
                deployableUnits.put(pomFile, artifactFile);
            } else {
                artifactFile = null;
            }
            final ArtifactPom artifactPomMock = context.mock(ArtifactPom.class, name);
            context.checking(new Expectations() {{
                allowing(artifactPomMock).getArtifactFile(); will(returnValue(artifactFile));
                allowing(artifactPomMock).getName(); will(returnValue(artifactFile == null ? pomFile.getName() : artifactFile.getName()));
                if (artifactFile != null) {
                    one(artifactPomMock).toPomFile(pomFile, testDependencies);
                }
            }});
            artifactPomContainer.addArtifactPom(artifactPomMock);
        }
    }

}
