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
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.publication.maven.internal.DefaultMavenDeployment;
import org.gradle.internal.Factory;
import org.jmock.Expectations;

import java.io.IOException;
import java.util.Set;

public class BaseMavenInstallerTest extends AbstractMavenResolverTest {
    private BaseMavenInstaller mavenInstaller;

    @SuppressWarnings("unchecked")
    private Factory<CustomInstallTask> installTaskFactoryMock = context.mock(Factory.class);
    private CustomInstallTask installTaskMock;

    protected BaseMavenInstaller createMavenInstaller() {
        return new BaseMavenInstaller(pomFilterContainerMock, artifactPomContainerMock, loggingManagerMock);
    }

    protected PomFilterContainer createPomFilterContainerMock() {
        return context.mock(PomFilterContainer.class);
    }

    protected AbstractMavenResolver getMavenResolver() {
        return mavenInstaller;
    }

    protected InstallDeployTaskSupport getInstallDeployTask() {
        return installTaskMock;
    }

    public void setUp() {
        super.setUp();
        installTaskMock = context.mock(CustomInstallTask.class);
        mavenInstaller = createMavenInstaller();
        mavenInstaller.setInstallTaskFactory(installTaskFactoryMock);
    }

    protected void checkTransaction(final Set<DefaultMavenDeployment> deployableUnits, AttachedArtifact attachedArtifact, PublishArtifact classifierArtifact) throws IOException, PlexusContainerException {
        context.checking(new Expectations() {
            {
                allowing(installTaskFactoryMock).create();
                will(returnValue(getInstallDeployTask()));
            }
        });
        super.checkTransaction(deployableUnits, attachedArtifact, classifierArtifact);
    }
}
