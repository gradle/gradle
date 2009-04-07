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
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.api.internal.artifacts.ConfigurationContainer;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractMavenResolverTest {
    public static final String TEST_NAME = "name";
    private static final Artifact TEST_IVY_ARTIFACT = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null);
    private static final File TEST_IVY_FILE = new File("somepom.xml");
    private static final File TEST_JAR_FILE = new File("somejar.jar");
    private static final Artifact TEST_ARTIFACT = new DefaultArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null, TEST_NAME, "jar", "jar");
    protected ArtifactPomContainer artifactPomContainerMock;
    protected PomFilterContainer pomFilterContainerMock;
    protected ConfigurationContainer configurationContainerMock;
    private Set<Configuration> testConfigurations;
    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    protected MavenPom pomMock;

    protected Settings mavenSettingsMock;

    protected abstract MavenResolver getMavenResolver();

    protected abstract InstallDeployTaskSupport getInstallDeployTask();

    protected abstract PomFilterContainer createPomFilterContainerMock();

    @Before
    public void setUp() {
        testConfigurations = new HashSet<Configuration>();
        configurationContainerMock = context.mock(ConfigurationContainer.class);
        pomFilterContainerMock = createPomFilterContainerMock();
        artifactPomContainerMock = context.mock(ArtifactPomContainer.class);
        pomMock = context.mock(MavenPom.class);
        mavenSettingsMock = context.mock(Settings.class);

        context.checking(new Expectations() {
            {
                allowing(configurationContainerMock).getAll(); will(returnValue(testConfigurations));
            }
        });
    }

    @Test
    public void deployOrInstall() throws IOException, PlexusContainerException {
        final HashMap<File, File> testDeployableUnits = new HashMap<File, File>() {
            {
                put(new File("pom1.xml"), new File("artifact1.jar"));
                put(new File("pom2.xml"), new File("artifact2.jar"));
            }
        };
        context.checking(new Expectations() {
            {
                allowing((CustomInstallDeployTaskSupport) getInstallDeployTask()).getSettings(); will(returnValue(mavenSettingsMock));
                one(artifactPomContainerMock).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
                allowing(artifactPomContainerMock).createDeployableUnits(testConfigurations); will(returnValue(testDeployableUnits));
            }
        });
        getMavenResolver().publish(TEST_IVY_ARTIFACT, TEST_IVY_FILE, true);
        getMavenResolver().publish(TEST_ARTIFACT, TEST_JAR_FILE, true);
        checkTransaction(testDeployableUnits);
        assertSame(mavenSettingsMock, getMavenResolver().getSettings());
    }

    protected void checkTransaction(final Map<File, File> deployableUnits) throws IOException, PlexusContainerException {
        context.checking(new Expectations() {
            {
                one(getInstallDeployTask()).setProject(with(any(Project.class)));
                for (File pomFile : deployableUnits.keySet()) {
                    one(getInstallDeployTask()).setFile(deployableUnits.get(pomFile));
                    one(getInstallDeployTask()).addPom(with(pomMatcher(pomFile)));
                    one(getInstallDeployTask()).execute();
                }
            }
        });
        getMavenResolver().commitPublishTransaction();
    }

    private static Matcher<Pom> pomMatcher(final File expectedPomFile) {
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

    @Test
    public void setFilter() {
        final PublishFilter publishFilterMock = context.mock(PublishFilter.class);
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).setFilter(publishFilterMock);
        }});
        getMavenResolver().setFilter(publishFilterMock);
    }

    @Test
    public void getFilter() {
        final PublishFilter publishFilterMock = context.mock(PublishFilter.class);
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getFilter(); will(returnValue(publishFilterMock));
        }});
        assertSame(publishFilterMock, getMavenResolver().getFilter());
    }

    @Test
    public void setPom() {
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).setPom(pomMock);
        }});
        getMavenResolver().setPom(pomMock);
    }

    @Test
    public void getPom() {
        context.checking(new Expectations() {{
            allowing(pomFilterContainerMock).getPom(); will(returnValue(pomMock));
        }});
        assertSame(pomMock, getMavenResolver().getPom());
    }

    @Test
    public void addFilter() {
        final String testName = "somename";
        final PublishFilter publishFilterMock = context.mock(PublishFilter.class);
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).addFilter(testName, publishFilterMock);
        }});
        getMavenResolver().addFilter(testName, publishFilterMock);
    }

    @Test
    public void filter() {
        final String testName = "somename";
        final PublishFilter publishFilterMock = context.mock(PublishFilter.class);
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).filter(testName); will(returnValue(publishFilterMock));
        }});
        assertSame(publishFilterMock, getMavenResolver().filter(testName));
    }

    @Test
    public void pom() {
        final String testName = "somename";
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).pom(testName); will(returnValue(pomMock));
        }});
        assertSame(pomMock, getMavenResolver().pom(testName));
    }
}
