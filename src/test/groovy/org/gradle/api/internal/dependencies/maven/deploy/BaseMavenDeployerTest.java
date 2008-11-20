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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import org.junit.runner.RunWith;
import org.gradle.util.WrapUtil;
import org.gradle.api.dependencies.maven.MavenResolver;
import org.jmock.Expectations;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;

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

    private BaseMavenDeployer mavenUploader;

    private DeployTaskFactory deployTaskFactoryMock;
    private CustomDeployTask deployTaskMock;

    private PlexusContainer plexusContainerMock;
    private RemoteRepository testRepository;
    private RemoteRepository testSnapshotRepository;

    protected BaseMavenDeployer createMavenDeployer() {
        return new BaseMavenDeployer(TEST_NAME, artifactPomContainerMock, dependencyManagerMock);
    }

    protected MavenResolver getMavenResolver() {
        return mavenUploader;
    }

    protected InstallDeployTaskSupport getInstallDeployTask() {
        return deployTaskMock;
    }

    public void setUp() {
        super.setUp();
        deployTaskFactoryMock = context.mock(DeployTaskFactory.class);
        deployTaskMock = context.mock(CustomDeployTask.class);
        plexusContainerMock = context.mock(PlexusContainer.class);
        testRepository = new RemoteRepository();
        testSnapshotRepository = new RemoteRepository();
        mavenUploader = createMavenDeployer();
        mavenUploader.setDeployTaskFactory(deployTaskFactoryMock);
        mavenUploader.setRepository(testRepository);
        mavenUploader.setSnapshotRepository(testSnapshotRepository);
        mavenUploader.addProtocolProviderJars(TEST_PROTOCOL_PROVIDER_JARS);
        mavenUploader.setUniqueVersion(false);
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
                one(deployTaskMock).setUniqueVersion(mavenUploader.isUniqueVersion());
                one(deployTaskMock).addRemoteRepository(testRepository);
                one(deployTaskMock).addRemoteSnapshotRepository(testSnapshotRepository);
            }
        });
        super.checkTransaction(deployableUnits);
    }

}
