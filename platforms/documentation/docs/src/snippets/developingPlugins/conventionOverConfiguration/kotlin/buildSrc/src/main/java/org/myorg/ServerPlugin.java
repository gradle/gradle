package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ServerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ServerExtension extension = project.getExtensions().create("server", ServerExtension.class);
        extension.getUrl().convention("https://www.myorg.com/server");
        project.getTasks().register("deploy", Deploy.class, deployTask -> deployTask.getUrl().set(extension.getUrl()));
    }
}
