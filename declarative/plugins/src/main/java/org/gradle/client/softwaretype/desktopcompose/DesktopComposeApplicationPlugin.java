package org.gradle.client.softwaretype.desktopcompose;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.experimental.kmp.StandaloneKmpApplicationPlugin;
import org.gradle.api.internal.plugins.software.SoftwareType;

@SuppressWarnings("UnstableApiUsage")
public abstract class DesktopComposeApplicationPlugin implements Plugin<Project> {
    public static final String DESKTOP_COMPOSE_APP = "desktopComposeApp";

    @SoftwareType(name = DESKTOP_COMPOSE_APP, modelPublicType = DesktopComposeApplication.class)
    public abstract DesktopComposeApplication getDesktopComposeApp();

    @Override
    public void apply(Project project) {
        DesktopComposeApplication dslModel = getDesktopComposeApp();
        StandaloneKmpApplicationPlugin.PluginWiring.wirePlugin(project, dslModel.getKotlinApplication());
    }
}
