package org.gradle.client.softwaretype.detekt;

import io.gitlab.arturbosch.detekt.extensions.DetektExtension;
import org.gradle.api.Project;

public final class DetektSupport {
    private DetektSupport() { /* not instantiable */ }

    public static void wireDetekt(Project project, Detekt dslModel) {
        project.getPluginManager().apply("io.gitlab.arturbosch.detekt");

        project.afterEvaluate(p -> {
            DetektExtension detekt = project.getExtensions().findByType(DetektExtension.class);
            detekt.getSource().from(dslModel.getSource());
            detekt.getConfig().from(dslModel.getConfig());

            // This is not a property, need to wire this in afterEvaluate, so might as well wait to wire the rest of detekt with it
            detekt.setParallel(dslModel.getParallel().get());
        });
    }
}
