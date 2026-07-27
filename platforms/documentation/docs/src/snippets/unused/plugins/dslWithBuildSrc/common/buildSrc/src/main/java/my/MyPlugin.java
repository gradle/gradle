package my;

import org.gradle.api.*;
import org.gradle.api.plugins.BasePlugin;

public class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
    }
}
