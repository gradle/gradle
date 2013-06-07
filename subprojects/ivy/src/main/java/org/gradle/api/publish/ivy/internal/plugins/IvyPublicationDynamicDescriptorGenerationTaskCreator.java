/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;

import java.io.File;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

public class IvyPublicationDynamicDescriptorGenerationTaskCreator {

    private final Project project;

    public IvyPublicationDynamicDescriptorGenerationTaskCreator(Project project) {
        this.project = project;
    }

    public void monitor(PublicationContainer publications) {
        publications.withType(IvyPublicationInternal.class).all(new Action<IvyPublicationInternal>() {
            public void execute(IvyPublicationInternal publication) {
                create(publication);
            }
        });
    }

    private void create(final IvyPublicationInternal publication) {
        String publicationName = publication.getName();

        String descriptorTaskName = calculateDescriptorTaskName(publicationName);
        GenerateIvyDescriptor descriptorTask = project.getTasks().create(descriptorTaskName, GenerateIvyDescriptor.class);
        descriptorTask.setDescription(String.format("Generates the Ivy Module Descriptor XML file for publication '%s'.", publication.getName()));
        descriptorTask.setDescriptor(publication.getDescriptor());

        ConventionMapping descriptorTaskConventionMapping = new DslObject(descriptorTask).getConventionMapping();
        descriptorTaskConventionMapping.map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "publications/" + publication.getName() + "/ivy.xml");
            }
        });

        publication.setDescriptorFile(descriptorTask.getOutputs().getFiles());
    }

    private String calculateDescriptorTaskName(String publicationName) {
        return String.format("generateDescriptorFileFor%sPublication", capitalize(publicationName));
    }

}
