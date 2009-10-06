package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.AntTest;
import org.gradle.api.tasks.testing.NativeTest;

/**
 * @author Tom Eyckmans
 */
public class StagingPlugin implements Plugin {

    public void use(final Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(JavaPlugin.class, project);

        TaskCollection<AntTest> antTestTasks = project.getTasks().withType(AntTest.class);
        for (AntTest antTestTask : antTestTasks) {
            project.getTasks().replace(antTestTask.getName(), NativeTest.class);
        }
        project.getTasks().withType(NativeTest.class).allTasks(new Action<NativeTest>() {
            public void execute(NativeTest test) {
                test.getConventionMapping().map(DefaultConventionsToPropertiesMapping.TEST);
            }
        });
    }
}
