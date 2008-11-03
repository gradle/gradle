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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import org.junit.runner.RunWith;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.PublishFilter;
import org.gradle.api.internal.dependencies.maven.MavenPomFactory;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.ant.Pom;
import org.apache.tools.ant.Project;
import org.hamcrest.Matcher;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BaseMavenUploaderTest {
    public static final String TEST_NAME = "name";
    private static final Artifact TEST_IVY_ARTIFACT = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null);
    private static final File TEST_IVY_FILE = new File("somepom.xml");
    private static final List<File> TEST_PROTOCOL_PROVIDER_JARS = WrapUtil.toList(new File("jar1"), new File("jar1"));
    private static final File TEST_JAR_FILE = new File("somejar.jar");
    private static final Artifact TEST_ARTIFACT = new DefaultArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null, TEST_NAME, "jar", "jar");

    protected BaseMavenUploader mavenUploader;

    private DeployTaskFactory deployTaskFactoryMock;
    private DeployTaskWithVisibleContainerProperty deployTaskMock;

    private PlexusContainer plexusContainerMock;
    private RemoteRepository testRepository;
    private RemoteRepository testSnapshotRepository;
    protected ArtifactPomContainer artifactPomContainerMock;
    private ArtifactPom defaultArtifactPomMock;
    protected DependencyManager dependencyManagerMock;
    private List<DependencyDescriptor> testDependencies;
    protected MavenPomFactory mavenPomFactoryMock;
    protected JUnit4Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    protected MavenPom pomMock;

    protected BaseMavenUploader createMavenUploader() {
        return new BaseMavenUploader(TEST_NAME, artifactPomContainerMock, mavenPomFactoryMock, dependencyManagerMock);
    }

    @Before
    public void setUp() {
        testDependencies = new ArrayList<DependencyDescriptor>();
        dependencyManagerMock = context.mock(DependencyManager.class);
        artifactPomContainerMock = context.mock(ArtifactPomContainer.class);
        defaultArtifactPomMock = context.mock(ArtifactPom.class);
        mavenPomFactoryMock = context.mock(MavenPomFactory.class);
        deployTaskFactoryMock = context.mock(DeployTaskFactory.class);
        deployTaskMock = context.mock(DeployTaskWithVisibleContainerProperty.class);
        plexusContainerMock = context.mock(PlexusContainer.class);
        pomMock = context.mock(MavenPom.class);
        testRepository = new RemoteRepository();
        testSnapshotRepository = new RemoteRepository();
        final ModuleDescriptor moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {
            {
                allowing(mavenPomFactoryMock).createMavenPom();
                will(returnValue(pomMock));
                one(artifactPomContainerMock).setDefaultArtifactPom(with(artifactPomMatcher(BaseMavenUploader.DEFAULT_ARTIFACT_POM_NAME,
                        pomMock,
                        PublishFilter.ALWAYS_ACCEPT)));
                allowing(dependencyManagerMock).createModuleDescriptor(true); will(returnValue(moduleDescriptorMock));
                allowing(moduleDescriptorMock).getDependencies(); will(returnValue(testDependencies.toArray(new DependencyDescriptor[testDependencies.size()])));
            }
        });
        mavenUploader = createMavenUploader();
        mavenUploader.setDeployTaskFactory(deployTaskFactoryMock);
        mavenUploader.setRepository(testRepository);
        mavenUploader.setSnapshotRepository(testSnapshotRepository);
        mavenUploader.addProtocolProviderJars(TEST_PROTOCOL_PROVIDER_JARS);
    }

    @Test
    public void deploy() throws IOException, PlexusContainerException {
        final HashMap<File, File> testDeployableUnits = new HashMap<File, File>() {
            {
                put(new File("pom1.xml"), new File("artifact1.jar"));
                put(new File("pom2.xml"), new File("artifact2.jar"));
            }
        };
        context.checking(new Expectations() {
            {
                one(artifactPomContainerMock).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
                allowing(artifactPomContainerMock).createDeployableUnits(testDependencies);
                will(returnValue(testDeployableUnits));
            }
        });
        mavenUploader.publish(TEST_IVY_ARTIFACT, TEST_IVY_FILE, true);
        mavenUploader.publish(TEST_ARTIFACT, TEST_JAR_FILE, true);
        checkTransaction(testDeployableUnits);
    }

    private void checkTransaction(final Map<File, File> deployableUnits) throws IOException, PlexusContainerException {
        context.checking(new Expectations() {
            {
                allowing(deployTaskFactoryMock).createDeployTask();
                will(returnValue(deployTaskMock));
                allowing(deployTaskMock).getContainer();
                will(returnValue(plexusContainerMock));
                for (File protocolProviderJar : TEST_PROTOCOL_PROVIDER_JARS) {
                    one(plexusContainerMock).addJarResource(protocolProviderJar);
                }
                one(deployTaskMock).setProject(with(any(Project.class)));
                one(deployTaskMock).addRemoteRepository(testRepository);
                one(deployTaskMock).addRemoteSnapshotRepository(testSnapshotRepository);
                for (File pomFile : deployableUnits.keySet()) {
                    one(deployTaskMock).setFile(deployableUnits.get(pomFile));
                    one(deployTaskMock).addPom(with(pomMatcher(pomFile)));
                    one(deployTaskMock).execute();
                }
            }
        });
        mavenUploader.commitPublishTransaction();
    }

    @Test(expected = InvalidUserDataException.class)
    public void getFilterWithNullName() {
        mavenUploader.filter(null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void getPomWithNullName() {
        mavenUploader.pom(null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addFilterWithNullName() {
        mavenUploader.addFilter(null, PublishFilter.ALWAYS_ACCEPT);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addFilterWithNullFilter() {
        mavenUploader.addFilter("somename", null);
    }

    @Test
    public void addFilter() {
        PublishFilter testFilter = createTestFilter();
        prepareFilterTest(testFilter);
        MavenPom pom = mavenUploader.addFilter(TEST_NAME, testFilter);
        assertSame(pom, pomMock);
    }

    private PublishFilter createTestFilter() {
        PublishFilter testFilter = new PublishFilter() {
            public boolean accept(Artifact artifact, File src) {
                return true;
            }
        };
        return testFilter;
    }

    protected void prepareFilterTest(final PublishFilter testFilter) {
        context.checking(new Expectations() {
            {
                one(artifactPomContainerMock).addArtifactPom(with(artifactPomMatcher(TEST_NAME, pomMock, testFilter)));
            }
        });
    }

    @Test
    public void setFilter() {
        final PublishFilter testFilter = createTestFilter();
        context.checking(new Expectations() {{
            allowing(artifactPomContainerMock).getDefaultArtifactPom(); will(returnValue(defaultArtifactPomMock));
            one(defaultArtifactPomMock).setFilter(testFilter);
        }});
        mavenUploader.setFilter(testFilter);
    }

    @Test
    public void setPom() {
        context.checking(new Expectations() {{
            allowing(artifactPomContainerMock).getDefaultArtifactPom(); will(returnValue(defaultArtifactPomMock));
            one(defaultArtifactPomMock).setPom(pomMock);
        }});
        mavenUploader.setPom(pomMock);
    }

    @Test
    public void getDefaultPomAndFilter() {
        context.checking(new Expectations() {
            {
                allowing(artifactPomContainerMock).getDefaultArtifactPom();
                will(returnValue(new DefaultArtifactPom(BaseMavenUploader.DEFAULT_ARTIFACT_POM_NAME, pomMock, PublishFilter.ALWAYS_ACCEPT)));
            }
        });
        assertSame(pomMock, mavenUploader.getPom());
        assertSame(PublishFilter.ALWAYS_ACCEPT, mavenUploader.getFilter());
    }

    @Test
    public void pomFilterByName() {
        final String testName = "testName";
        context.checking(new Expectations() {
            {
                allowing(artifactPomContainerMock).getArtifactPom(testName);
                will(returnValue(new DefaultArtifactPom(testName, pomMock, PublishFilter.ALWAYS_ACCEPT)));
            }
        });
        assertSame(pomMock, mavenUploader.pom(testName));
        assertSame(PublishFilter.ALWAYS_ACCEPT, mavenUploader.filter(testName));
    }

    private Matcher<Pom> pomMatcher(final File expectedPomFile) {
        return new BaseMatcher<Pom>() {
            public void describeTo(Description description) {
                description.appendText("matching pom");
            }

            public boolean matches(Object actual) {
                Pom actualPom = (Pom) actual;
                return actualPom.getFile().equals(expectedPomFile);
            }
        };
    }

    protected Matcher<DefaultArtifactPom> artifactPomMatcher(final String name, final MavenPom mavenPom, final PublishFilter filter) {
        return new BaseMatcher<DefaultArtifactPom>() {
            public void describeTo(Description description) {
                description.appendText("matching artifactPom");
            }

            public boolean matches(Object actual) {
                ArtifactPom actualArtifactPom = (ArtifactPom) actual;
                return actualArtifactPom.getName().equals(name) &&
                        actualArtifactPom.getPom() == mavenPom &&
                        actualArtifactPom.getFilter() == filter;
            }
        };
    }


}
