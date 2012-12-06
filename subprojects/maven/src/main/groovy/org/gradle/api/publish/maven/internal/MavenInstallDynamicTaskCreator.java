/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.maven.tasks.InstallToMavenLocal;
import org.gradle.api.tasks.TaskContainer;

import static org.apache.commons.lang.StringUtils.capitalize;

public class MavenInstallDynamicTaskCreator {

    final private TaskContainer tasks;
    private final Task installToMavenLocalTask;

    public MavenInstallDynamicTaskCreator(TaskContainer tasks, Task installToMavenLocalTask) {
        this.tasks = tasks;
        this.installToMavenLocalTask = installToMavenLocalTask;
    }

    public void monitor(final PublicationContainer publications) {
        final NamedDomainObjectSet<MavenPublicationInternal> mavenPublications = publications.withType(MavenPublicationInternal.class);

        mavenPublications.all(new Action<MavenPublicationInternal>() {
            public void execute(MavenPublicationInternal publication) {
                createInstallTask(publication);
            }
        });
        // Note: we aren't supporting removal of publications
    }

    private void createInstallTask(MavenPublicationInternal publication) {
        String publicationName = publication.getName();
        String installTaskName = calculateInstallTaskName(publicationName);

        InstallToMavenLocal installTask = tasks.add(installTaskName, InstallToMavenLocal.class);
        installTask.setPublication(publication);
        installTask.setGroup("publishing");
        installTask.setDescription(String.format("Installs Maven publication '%s' to Maven Local repository", publicationName));

        installToMavenLocalTask.dependsOn(installTask);
    }

    private String calculateInstallTaskName(String publicationName) {
        return String.format("install%sPublicationToMavenLocal", capitalize(publicationName));
    }
}
