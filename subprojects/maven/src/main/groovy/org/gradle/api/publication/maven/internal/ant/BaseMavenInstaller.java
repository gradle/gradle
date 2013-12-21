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

import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.InstallTask;
import org.apache.tools.ant.Project;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.logging.LoggingManagerInternal;

public class BaseMavenInstaller extends AbstractMavenResolver {
    public BaseMavenInstaller(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        super(pomFilterContainer, artifactPomContainer, loggingManager);
        mavenSettingsSupplier = new MaybeUserMavenSettingsSupplier();
    }

    protected InstallDeployTaskSupport createPreConfiguredTask(Project project) {
        InstallTask installTask = createTask();
        installTask.setProject(project);
        return installTask;
    }

    protected CustomInstallTask createTask() {
        return new CustomInstallTask();
    }
}
