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

package org.gradle.api.internal.plugins;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Upload;

import java.io.File;
import java.util.concurrent.Callable;

public class UploadRule extends AbstractRule {

    public static final String PREFIX = "upload";

    private final Project project;

    public UploadRule(Project project) {
        this.project = project;
    }

    public String getDescription() {
        return "Pattern: " + PREFIX + "<ConfigurationName>: Assembles and uploads the artifacts belonging to a configuration.";
    }

    public void apply(String taskName) {
        if (taskName.startsWith(PREFIX)) {
            for (Configuration configuration : project.getConfigurations()) {
                if (taskName.equals(configuration.getUploadTaskName())) {
                    createUploadTask(configuration.getUploadTaskName(), configuration, project);
                }
            }
        }
    }

    private Upload createUploadTask(String name, final Configuration configuration, final Project project) {
        Upload upload = project.getTasks().create(name, Upload.class);
        upload.setDescription("Uploads all artifacts belonging to " + configuration);
        upload.setGroup(BasePlugin.UPLOAD_GROUP);
        upload.setConfiguration(configuration);
        upload.setUploadDescriptor(true);
        upload.getConventionMapping().map("descriptorDestination", new Callable<File>() {
            public File call() throws Exception {
                return new File(project.getBuildDir(), "ivy.xml");
            }
        });
        return upload;
    }

}
