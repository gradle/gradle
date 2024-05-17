package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SitePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getExtensions().create("site", SiteExtension.class);
    }
}
