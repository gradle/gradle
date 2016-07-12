package org.gradle.ide.cdt;

import groovy.lang.Closure;
import groovy.transform.CompileStatic;
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

@Incubating
@CompileStatic
public class CdtIdePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        final ArrayList<GenerateMetadataFileTask> metadataFileTasks = new ArrayList<GenerateMetadataFileTask>(Arrays.asList(addCreateProjectDescriptor(project), addCreateCprojectDescriptor(project)));

        project.getTasks().create("cleanCdt", Delete.class, new Closure<Delete>(this, this) {
            public Delete doCall(Object clean) {
                return ((Delete) clean).delete(((ArrayList<GenerateMetadataFileTask>) metadataFileTasks).getOutputs().getFiles());
            }

        });

        project.getTasks().create("cdt", new Closure<Task>(this, this) {
            public Task doCall(Object cdt) {
                return ((Task) cdt).dependsOn(metadataFileTasks);
            }

        });
    }

    private GenerateMetadataFileTask addCreateProjectDescriptor(final Project project) {
        GenerateMetadataFileTask task = project.getTasks().create("cdtProject", GenerateMetadataFileTask.class);
        task.setInputFile(project.file(".project"));
        task.setOutputFile(project.file(".project"));
        task.factory(new Closure<ProjectDescriptor>(this, this) {
            public ProjectDescriptor doCall(Object it) {
                return new ProjectDescriptor();
            }

            public ProjectDescriptor doCall() {
                return doCall(null);
            }

        });
        task.onConfigure(new Closure<Void>(this, this) {
            public void doCall(Object it) {
                ProjectSettings settings = new ProjectSettings();

                settings.setName(project.getName()).applyTo((ProjectDescriptor) it);
            }

            public void doCall() {
                doCall(null);
            }

        });
        return task;
    }

    private GenerateMetadataFileTask addCreateCprojectDescriptor(final Project project) {
        return project.getTasks().create("cdtCproject", GenerateMetadataFileTask.class, new Closure<Void>(this, this) {
            public void doCall(final Object task) {

                ((ArrayList<ComponentSpecContainer>) new ArrayList<ComponentSpecContainer>(Arrays.asList(project.getExtensions().getByType(ComponentSpecContainer.class)))).all(new Closure<CprojectSettings>(CdtIdePlugin.this, CdtIdePlugin.this) {
                    public CprojectSettings doCall(Object binary) {
                        if (((ComponentSpec) binary).getName().equals("main")) {
                            return setSettings(task, new CprojectSettings((NativeComponentSpec) binary, (ProjectInternal) project));
                        }

                    }

                });

                ((GenerateMetadataFileTask) task).doFirst(new Closure<Void>(CdtIdePlugin.this, CdtIdePlugin.this) {
                    public void doCall(Task it) {
                        if (((GenerateMetadataFileTask) task).getSettings() == null) {
                            throw new InvalidUserDataException("There is neither a main binary or library");
                        }

                    }

                    public void doCall() {
                        doCall(null);
                    }

                });

                ((GenerateMetadataFileTask) task).getInputs().files(new Closure<FileCollection>(CdtIdePlugin.this, CdtIdePlugin.this) {
                    public FileCollection doCall(Object it) {
                        return ((GenerateMetadataFileTask) task).getSettings().getIncludeRoots();
                    }

                    public FileCollection doCall() {
                        return doCall(null);
                    }

                }).withPropertyName("settings.includeRoots");
                ((GenerateMetadataFileTask) task).setInputFile(project.file(".cproject"));
                ((GenerateMetadataFileTask) task).setOutputFile(project.file(".cproject"));
                ((GenerateMetadataFileTask) task).factory(new Closure<CprojectDescriptor>(CdtIdePlugin.this, CdtIdePlugin.this) {
                    public CprojectDescriptor doCall(Object it) {
                        return new CprojectDescriptor();
                    }

                    public CprojectDescriptor doCall() {
                        return doCall(null);
                    }

                });
                ((GenerateMetadataFileTask) task).onConfigure(new Closure<Void>(CdtIdePlugin.this, CdtIdePlugin.this) {
                    public void doCall(Object descriptor) {
                        ((GenerateMetadataFileTask) task).getSettings().applyTo((CprojectDescriptor) descriptor);
                    }

                });
            }

        });
    }

    private static <Value extends CprojectSettings> Value setSettings(GenerateMetadataFileTask propOwner, Value settings) {
        propOwner.setSettings(settings);
        return settings;
    }
}
