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

import org.gradle.api.dependencies.maven.PomFilterContainer;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.PublishFilter;
import org.gradle.api.dependencies.maven.MavenResolver;
import org.gradle.api.DependencyManager;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.dependencies.maven.MavenPomFactory;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.tools.ant.Project;
import org.junit.Before;
import org.junit.Test;import static org.junit.Assert.assertSame;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.codehaus.plexus.PlexusContainerException;
import org.hamcrest.Matcher;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.File;

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
    protected DependencyManager dependencyManagerMock;
    private List<DependencyDescriptor> testDependencies;
    protected JUnit4Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    protected MavenPom pomMock;

    protected Settings mavenSettingsMock;

    protected abstract MavenResolver getMavenResolver();

    protected abstract InstallDeployTaskSupport getInstallDeployTask();

    @Before
    public void setUp() {
        testDependencies = new ArrayList<DependencyDescriptor>();
        dependencyManagerMock = context.mock(DependencyManager.class);
        artifactPomContainerMock = context.mock(ArtifactPomContainer.class);
        pomMock = context.mock(MavenPom.class);
        mavenSettingsMock = context.mock(Settings.class);

        final ModuleDescriptor moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {
            {
                allowing(dependencyManagerMock).createModuleDescriptor(true); will(returnValue(moduleDescriptorMock));
                allowing(moduleDescriptorMock).getDependencies(); will(returnValue(testDependencies.toArray(new DependencyDescriptor[testDependencies.size()])));
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
                allowing(artifactPomContainerMock).createDeployableUnits(testDependencies); will(returnValue(testDeployableUnits));
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
}
