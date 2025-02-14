package org.gradle.client.softwaretype.compose;

import org.gradle.api.Project;
import org.gradle.client.softwaretype.CustomDesktopComposeApplication;

public final class ComposeSupport {
    private ComposeSupport() { /* not instantiable */ }

    public static void wireCompose(Project project, CustomDesktopComposeApplication dslModel) {
        project.getPluginManager().apply("org.jetbrains.compose");
        project.getPluginManager().apply("org.jetbrains.kotlin.plugin.compose");
    }
}
