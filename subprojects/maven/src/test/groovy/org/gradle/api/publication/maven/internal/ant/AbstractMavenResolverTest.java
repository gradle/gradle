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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.Action;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenDeployment;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.api.publication.maven.internal.DefaultMavenDeployment;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

public abstract class AbstractMavenResolverTest {
    public static final String TEST_NAME = "name";
    private static final Artifact TEST_IVY_ARTIFACT = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null);
    private static final File TEST_IVY_FILE = new File("somepom.xml");
    private static final File TEST_JAR_FILE = new File("somejar.jar");
    private static final Artifact TEST_ARTIFACT = new DefaultArtifact(ModuleRevisionId.newInstance("org", TEST_NAME, "1.0"), null, TEST_NAME, "jar", "jar");
    protected ArtifactPomContainer artifactPomContainerMock;
    protected PomFilterContainer pomFilterContainerMock;
    protected LoggingManagerInternal loggingManagerMock;

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    protected MavenPom pomMock;

    protected Settings mavenSettingsMock;

    protected abstract AbstractMavenResolver getMavenResolver();

    protected abstract InstallDeployTaskSupport getInstallDeployTask();

    protected abstract PomFilterContainer createPomFilterContainerMock();

    @Before
    public void setUp() {
        pomFilterContainerMock = createPomFilterContainerMock();
        artifactPomContainerMock = context.mock(ArtifactPomContainer.class);
        pomMock = context.mock(MavenPom.class);
        mavenSettingsMock = context.mock(Settings.class);
        loggingManagerMock = context.mock(LoggingManagerInternal.class);
    }

    @Test
    public void deployOrInstall() throws IOException, PlexusContainerException {
        getMavenResolver().mavenSettingsSupplier = context.mock(MavenSettingsSupplier.class);

        PublishArtifact classifierArtifact = artifact(new File("classifier.jar"));
        final DefaultMavenDeployment deployment1 = new DefaultMavenDeployment(artifact(new File("pom1.pom")), artifact(new File("artifact1.jar")), Collections.<PublishArtifact>emptySet());
        final DefaultMavenDeployment deployment2 = new DefaultMavenDeployment(artifact(new File("pom2.pom")), artifact(new File("artifact2.jar")), WrapUtil.toSet(classifierArtifact));
        final Set<DefaultMavenDeployment> testDefaultMavenDeployments = WrapUtil.toSet(
                deployment1,
                deployment2
        );
        final AttachedArtifact attachedArtifact = new AttachedArtifact();
        @SuppressWarnings("unchecked")
        final Action<MavenDeployment> action = context.mock(Action.class);

        context.checking(new Expectations() {
            {
                allowing((CustomInstallDeployTaskSupport) getInstallDeployTask()).clearAttachedArtifactsList();
                allowing((CustomInstallDeployTaskSupport) getInstallDeployTask()).getSettings();
                will(returnValue(mavenSettingsMock));
                allowing((CustomInstallDeployTaskSupport) getInstallDeployTask()).getProject();
                will(returnValue(AntUtil.createProject()));
                allowing((CustomInstallDeployTaskSupport) getInstallDeployTask()).createAttach();
                will(returnValue(attachedArtifact));
                one(artifactPomContainerMock).addArtifact(TEST_ARTIFACT, TEST_JAR_FILE);
                allowing(artifactPomContainerMock).createDeployableFilesInfos();
                will(returnValue(testDefaultMavenDeployments));
                one(action).execute(deployment1);
                one(action).execute(deployment2);
            }
        });

        getMavenResolver().beforeDeployment(action);
        getMavenResolver().publish(TEST_IVY_ARTIFACT, TEST_IVY_FILE, true);
        getMavenResolver().publish(TEST_ARTIFACT, TEST_JAR_FILE, true);
        checkTransaction(testDefaultMavenDeployments, attachedArtifact, classifierArtifact);
        assertSame(mavenSettingsMock, getMavenResolver().getSettings());
    }

    private PublishArtifact artifact(File file) {
        return new DefaultPublishArtifact("name", "ext", "type", null, null, file);
    }

    protected void checkTransaction(final Set<DefaultMavenDeployment> defaultMavenDeployments, final AttachedArtifact attachedArtifact, PublishArtifact classifierArtifact) throws IOException, PlexusContainerException {
        context.checking(new Expectations() {
            {
                one(getInstallDeployTask()).setProject(with(any(Project.class)));
                for (DefaultMavenDeployment defaultMavenDeployment : defaultMavenDeployments) {
                    one(getInstallDeployTask()).setFile(defaultMavenDeployment.getMainArtifact().getFile());
                    one(getInstallDeployTask()).addPom(with(pomMatcher(defaultMavenDeployment.getPomArtifact().getFile(), getInstallDeployTask().getProject())));
                    one(loggingManagerMock).captureStandardOutput(LogLevel.INFO);
                    will(returnValue(loggingManagerMock));
                    one(loggingManagerMock).start();
                    one(getInstallDeployTask()).execute();
                    one(loggingManagerMock).stop();
                    will(returnValue(loggingManagerMock));
                }
                one(getMavenResolver().mavenSettingsSupplier).supply(getInstallDeployTask());
                one(getMavenResolver().mavenSettingsSupplier).done();
            }
        });
        getMavenResolver().commitPublishTransaction();
        assertThat(attachedArtifact.getFile(), equalTo(classifierArtifact.getFile()));
        assertThat(attachedArtifact.getType(), equalTo(classifierArtifact.getType()));
        assertThat(attachedArtifact.getClassifier(), equalTo(classifierArtifact.getClassifier()));
    }

    private static Matcher<Pom> pomMatcher(final File expectedPomFile, final Project expectedAntProject) {
        return new BaseMatcher<Pom>() {
            public void describeTo(Description description) {
                description.appendText("matching pom");
            }

            public boolean matches(Object actual) {
                Pom actualPom = (Pom) actual;
                return actualPom.getFile().equals(expectedPomFile) && actualPom.getProject().equals(expectedAntProject);
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
            allowing(pomFilterContainerMock).getFilter();
            will(returnValue(publishFilterMock));
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
            allowing(pomFilterContainerMock).getPom();
            will(returnValue(pomMock));
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
            one(pomFilterContainerMock).filter(testName);
            will(returnValue(publishFilterMock));
        }});
        assertSame(publishFilterMock, getMavenResolver().filter(testName));
    }

    @Test
    public void pom() {
        final String testName = "somename";
        context.checking(new Expectations() {{
            one(pomFilterContainerMock).pom(testName);
            will(returnValue(pomMock));
        }});
        assertSame(pomMock, getMavenResolver().pom(testName));
    }
}
