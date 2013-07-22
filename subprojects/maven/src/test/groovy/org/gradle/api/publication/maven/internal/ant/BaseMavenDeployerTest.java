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

import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.publication.maven.internal.DefaultMavenDeployment;
import org.gradle.internal.Factory;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertTrue;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class BaseMavenDeployerTest extends AbstractMavenResolverTest {

    private BaseMavenDeployer mavenDeployer = createMavenDeployer();

    @SuppressWarnings("unchecked")
    private Factory<CustomDeployTask> deployTaskFactoryMock = context.mock(Factory.class);
    private CustomDeployTask deployTaskMock = context.mock(CustomDeployTask.class);

    private PlexusContainer plexusContainerMock = context.mock(PlexusContainer.class);
    private RemoteRepository testRepository = new RemoteRepository();
    private RemoteRepository testSnapshotRepository = new RemoteRepository();

    private Configuration configurationStub = context.mock(Configuration.class);

    protected BaseMavenDeployer createMavenDeployer() {
        return new BaseMavenDeployer(pomFilterContainerMock, artifactPomContainerMock, loggingManagerMock);
    }

    protected AbstractMavenResolver getMavenResolver() {
        return mavenDeployer;
    }

    protected InstallDeployTaskSupport getInstallDeployTask() {
        return deployTaskMock;
    }

    protected PomFilterContainer createPomFilterContainerMock() {
        return context.mock(PomFilterContainer.class);
    }

    public void setUp() {
        super.setUp();
        mavenDeployer = createMavenDeployer();
        mavenDeployer.setDeployTaskFactory(deployTaskFactoryMock);
        mavenDeployer.setRepository(testRepository);
        mavenDeployer.setSnapshotRepository(testSnapshotRepository);
        mavenDeployer.setConfiguration(configurationStub);
        mavenDeployer.setUniqueVersion(false);
    }

    protected void checkTransaction(final Set<DefaultMavenDeployment> defaultMavenDeployments, AttachedArtifact attachedArtifact, PublishArtifact classifierArtifact) throws IOException, PlexusContainerException {
        final Set<File> protocolJars = WrapUtil.toLinkedSet(new File("jar1"), new File("jar1"));
        context.checking(new Expectations() {{
                allowing(configurationStub).resolve();
                will(returnValue(protocolJars));
                allowing(deployTaskFactoryMock).create();
                will(returnValue(getInstallDeployTask()));
                allowing(deployTaskMock).getContainer();
                will(returnValue(plexusContainerMock));
                for (File protocolProviderJar : protocolJars) {
                    one(plexusContainerMock).addJarResource(protocolProviderJar);
                }
                one(deployTaskMock).setUniqueVersion(mavenDeployer.isUniqueVersion());
                one(deployTaskMock).addRemoteRepository(testRepository);
                one(deployTaskMock).addRemoteSnapshotRepository(testSnapshotRepository);
        }});
        super.checkTransaction(defaultMavenDeployments, attachedArtifact, classifierArtifact);
    }

    @Test
    public void init() {
        mavenDeployer = new BaseMavenDeployer(pomFilterContainerMock, artifactPomContainerMock, loggingManagerMock);
        assertTrue(mavenDeployer.isUniqueVersion());
    }

}
