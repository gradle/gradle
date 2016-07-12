/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.ide.cdt;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.Delete;
import org.gradle.ide.cdt.model.CprojectDescriptor;
import org.gradle.ide.cdt.model.CprojectSettings;
import org.gradle.ide.cdt.model.ProjectDescriptor;
import org.gradle.ide.cdt.model.ProjectSettings;
import org.gradle.ide.cdt.tasks.GenerateMetadataFileTask;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Generates configuration files for Eclipse CDT.
 */
@Incubating
public class CdtIdePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        final ArrayList<GenerateMetadataFileTask> metadataFileTasks = new ArrayList<GenerateMetadataFileTask>(Arrays.asList(addCreateProjectDescriptor(project), addCreateCprojectDescriptor(project)));

        Delete cleanCdt = project.getTasks().create("cleanCdt", Delete.class);
        for (GenerateMetadataFileTask metadataFileTask : metadataFileTasks) {
            cleanCdt.delete(metadataFileTask.getOutputs().getFiles());
        }

        Task cdt = project.getTasks().create("cdt");
        cdt.dependsOn(metadataFileTasks);
    }

    private GenerateMetadataFileTask addCreateProjectDescriptor(final Project project) {
        GenerateMetadataFileTask task = project.getTasks().create("cdtProject", GenerateMetadataFileTask.class);
        task.setInputFile(project.file(".project"));
        task.setOutputFile(project.file(".project"));
        task.factory(new Closure<ProjectDescriptor>(this, this) {
            public ProjectDescriptor doCall(Object it) {
                return new ProjectDescriptor();
            }

        });
        task.onConfigure(new Closure<Void>(this, this) {
            public void doCall(Object it) {
                ProjectSettings settings = new ProjectSettings();
                settings.setName(project.getName());
                settings.applyTo((ProjectDescriptor) it);
            }

        });
        return task;
    }

    private GenerateMetadataFileTask addCreateCprojectDescriptor(final Project project) {
        final GenerateMetadataFileTask task = project.getTasks().create("cdtCproject", GenerateMetadataFileTask.class);
            project.getExtensions().getByType(ComponentSpecContainer.class).all(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    if (componentSpec.getName().equals("main")) {
                        task.setSettings(new CprojectSettings((NativeComponentSpec) componentSpec, (ProjectInternal) project));
                    }
                }
            });

            task.doFirst(new Closure<Void>(CdtIdePlugin.this, CdtIdePlugin.this) {
                public void doCall(Task it) {
                    if (task.getSettings() == null) {
                        throw new InvalidUserDataException("There is neither a main binary or library");
                    }
                }

            });

            task.getInputs()
                .files(new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return task.getSettings().getIncludeRoots();
                    }
                })
                .withPropertyName("settings.includeRoots");
            task.setInputFile(project.file(".cproject"));
            task.setOutputFile(project.file(".cproject"));
            task.factory(new Closure<CprojectDescriptor>(CdtIdePlugin.this, CdtIdePlugin.this) {
                public CprojectDescriptor doCall(Object it) {
                    return new CprojectDescriptor();
                }

            });
            task.onConfigure(new Closure<Void>(CdtIdePlugin.this, CdtIdePlugin.this) {
                public void doCall(Object descriptor) {
                    task.getSettings().applyTo((CprojectDescriptor) descriptor);
                }

            });
        return task;
    }
}
