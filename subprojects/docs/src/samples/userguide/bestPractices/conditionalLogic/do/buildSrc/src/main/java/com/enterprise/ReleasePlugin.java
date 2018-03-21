package com.enterprise;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class ReleasePlugin implements Plugin<Project> {
    private static final String RELEASE_ENG_ROLE_SYS_PROP = "release.engineer";
    private static final String RELEASE_TASK_NAME = "release";

    @Override
    public void apply(Project project) {
        if (Boolean.getBoolean(RELEASE_ENG_ROLE_SYS_PROP)) {
            Task task = project.getTasks().create(RELEASE_TASK_NAME);

            task.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    // release the artifact to production
                    task.getLogger().quiet("Releasing to production...");
                }
            });
        }
    }
}
