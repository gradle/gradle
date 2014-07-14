package org.gradle.nativebinaries.language.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.nativebinaries.ProjectNativeBinary;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.runtime.base.BinaryContainer;

public class CreateSourceTransformTask {
    public CreateSourceTransformTask(LanguageRegistration<? extends LanguageSourceSet> languageRegistration) {
        this.language = languageRegistration;
    }

    public void init(final Project project) {
        BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        binaries.withType(ProjectNativeBinaryInternal.class).all(new Action<ProjectNativeBinaryInternal>() {
            public void execute(ProjectNativeBinaryInternal binary) {
                createCompileTasks(project.getTasks(), binary);
            }
        });
    }

    public void createCompileTasks(final TaskContainer tasks, ProjectNativeBinary projectNativeBinary) {
        final ProjectNativeBinaryInternal binary = (ProjectNativeBinaryInternal) projectNativeBinary;
        final SourceTransformTaskConfig taskConfig = language.getTransformTask();
        binary.getSource().withType(language.getSourceSetType(), new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet languageSourceSet) {
                LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                if (sourceSet.getMayHaveSources()) {
                    String taskName = binary.getNamingScheme().getTaskName(taskConfig.getTaskPrefix(), sourceSet.getFullName());
                    Task task = tasks.create(taskName, taskConfig.getTaskType());

                    taskConfig.configureTask(task, binary, sourceSet);

                    task.dependsOn(sourceSet);
                    binary.getTasks().add(task);
                }

            }

        });
    }

    private final LanguageRegistration<? extends LanguageSourceSet> language;
}
