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

package org.gradle.api.publish.maven.internal.plugins;

import  org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.maven.internal.MavenPublicationInternal;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;

import java.io.File;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

public class GeneratePomTaskCreator {

    private final Project project;

    public GeneratePomTaskCreator(Project project) {
        this.project = project;
    }

    public void monitor(PublicationContainer publications) {
        publications.withType(MavenPublicationInternal.class).all(new Action<MavenPublicationInternal>() {
            public void execute(MavenPublicationInternal publication) {
                create(publication);
            }
        });
    }

    private void create(final MavenPublicationInternal publication) {
        String publicationName = publication.getName();

        String descriptorTaskName = calculateDescriptorTaskName(publicationName);
        GenerateMavenPom generatePomTask = project.getTasks().add(descriptorTaskName, GenerateMavenPom.class);
        generatePomTask.setDescription(String.format("Generates the Maven POM file for publication '%s'.", publication.getName()));
        generatePomTask.setPom(publication.getPom());

        ConventionMapping descriptorTaskConventionMapping = new DslObject(generatePomTask).getConventionMapping();
        descriptorTaskConventionMapping.map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "publications/" + publication.getName() + "/pom-default.xml");
            }
        });

        // Wire the generated pom into the publication.
        publication.setPomFile(generatePomTask.getOutputs().getFiles());
    }

    private String calculateDescriptorTaskName(String publicationName) {
        return String.format("generatePomFileFor%sPublication", capitalize(publicationName));
    }

}
