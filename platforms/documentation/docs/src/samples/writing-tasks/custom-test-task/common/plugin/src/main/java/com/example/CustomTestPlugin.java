package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.ReportingExtension;

/**
 * Registers a test task to demonstrate the {@code TestEventReporter} API.
 */
public abstract class CustomTestPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {

        project.getPluginManager().apply(ReportingBasePlugin.class);
        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);

        project.getTasks().register("test", CustomTest.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs the tests.");
            task.getBinaryResultsDirectory().convention(project.getLayout().getBuildDirectory().dir("test-results/test"));
            task.getHtmlReportDirectory().convention(reporting.getBaseDirectory().dir("tests/test"));
            // Enables Test UI in IntelliJ
            task.getExtensions().getExtraProperties().set("idea.internal.test", true);
            task.getFail().convention(false);
        });

    }
}
