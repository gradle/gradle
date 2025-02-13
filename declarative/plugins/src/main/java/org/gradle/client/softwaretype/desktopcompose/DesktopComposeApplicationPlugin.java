package org.gradle.client.softwaretype.desktopcompose;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.client.softwaretype.detekt.Detekt;

import static org.gradle.api.experimental.kmp.StandaloneKmpApplicationPlugin.PluginWiring.wireKMPApplication;
import static org.gradle.client.softwaretype.detekt.DetektSupport.wireDetekt;
import static org.gradle.client.softwaretype.sqldelight.SqlDelightSupport.needToWireSqlDelight;
import static org.gradle.client.softwaretype.sqldelight.SqlDelightSupport.wireSqlDelight;

@SuppressWarnings("UnstableApiUsage")
public abstract class DesktopComposeApplicationPlugin implements Plugin<Project> {
    public static final String DESKTOP_COMPOSE_APP = "desktopComposeApp";

    @SoftwareType(name = DESKTOP_COMPOSE_APP, modelPublicType = DesktopComposeApplication.class)
    public abstract DesktopComposeApplication getDesktopComposeApp();

    @Override
    public void apply(Project project) {
        DesktopComposeApplication dslModel = getDesktopComposeApp();

        project.setGroup(dslModel.getGroup());
        project.setVersion(dslModel.getVersion());

        wireKMPApplication(project, dslModel.getKotlinApplication());
        wireDetekt(project, dslModel.getDetekt());

        /*
         * It's necessary to defer checking the NDOC in our extension for contents until after project evaluation.
         * If you move the check below outside of afterEvaluate, it fails.  Inside, it succeeds.
         * Without the afterEvaluate, the databases is seen as empty, and the plugin fails, with this warning:
         * https://github.com/plangrid/sqldelight/blob/917cb8e5ee437d37bfdbdcbb3fded09b683fe826/sqldelight-gradle-plugin/src/main/kotlin/app/cash/sqldelight/gradle/SqlDelightPlugin.kt#L112
         */
        project.afterEvaluate(p -> {
            if (needToWireSqlDelight(dslModel)) {
                wireSqlDelight(project, dslModel);
            }
        });
    }
}
