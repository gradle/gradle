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

import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BaseMavenDeployerTest extends AbstractMavenResolverTest {
    private static final List<File> TEST_PROTOCOL_PROVIDER_JARS = WrapUtil.toList(new File("jar1"), new File("jar1"));

    private BaseMavenDeployer mavenDeployer;

    private DeployTaskFactory deployTaskFactoryMock;
    private CustomDeployTask deployTaskMock;

    private PlexusContainer plexusContainerMock;
    private RemoteRepository testRepository;
    private RemoteRepository testSnapshotRepository;

    protected BaseMavenDeployer createMavenDeployer() {
        return new BaseMavenDeployer(TEST_NAME, pomFilterContainerMock, artifactPomContainerMock, configurationContainerMock);
    }

    protected MavenResolver getMavenResolver() {
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
        deployTaskFactoryMock = context.mock(DeployTaskFactory.class);
        deployTaskMock = context.mock(CustomDeployTask.class);
        plexusContainerMock = context.mock(PlexusContainer.class);
        testRepository = new RemoteRepository();
        testSnapshotRepository = new RemoteRepository();
        mavenDeployer = createMavenDeployer();
        mavenDeployer.setDeployTaskFactory(deployTaskFactoryMock);
        mavenDeployer.setRepository(testRepository);
        mavenDeployer.setSnapshotRepository(testSnapshotRepository);
        mavenDeployer.addProtocolProviderJars(TEST_PROTOCOL_PROVIDER_JARS);
        mavenDeployer.setUniqueVersion(false);
    }

    protected void checkTransaction(final Map<File, File> deployableUnits) throws IOException, PlexusContainerException {
        context.checking(new Expectations() {
            {
                allowing(deployTaskFactoryMock).createDeployTask();
                will(returnValue(getInstallDeployTask()));
                allowing(deployTaskMock).getContainer();
                will(returnValue(plexusContainerMock));
                for (File protocolProviderJar : TEST_PROTOCOL_PROVIDER_JARS) {
                    one(plexusContainerMock).addJarResource(protocolProviderJar);
                }
                one(deployTaskMock).setUniqueVersion(mavenDeployer.isUniqueVersion());
                one(deployTaskMock).addRemoteRepository(testRepository);
                one(deployTaskMock).addRemoteSnapshotRepository(testSnapshotRepository);
            }
        });
        super.checkTransaction(deployableUnits);
    }

    @Test
    public void init() {
        mavenDeployer = new BaseMavenDeployer(TEST_NAME, pomFilterContainerMock, artifactPomContainerMock, configurationContainerMock);
        assertTrue(mavenDeployer.isUniqueVersion());
    }

}
