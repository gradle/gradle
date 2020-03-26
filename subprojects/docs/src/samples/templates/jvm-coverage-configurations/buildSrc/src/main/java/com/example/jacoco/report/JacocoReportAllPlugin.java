package com.example.jacoco.report;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testing.jacoco.tasks.JacocoReport;

public class JacocoReportAllPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final JacocoReportConfigurations configurations = JacocoReportConfigurations.forConsumers(project);
        configurations.createRootConfiguration();
        createReportCoverageTask(project, configurations);
    }

    private void createReportCoverageTask(Project project, JacocoReportConfigurations configurations) {
        project.getTasks().register("codeCoverageReport", JacocoReport.class, reportTask -> {
            reportTask.additionalClassDirs(configurations.createCoverageRuntimeConfiguration());
            reportTask.additionalSourceDirs(configurations.createCoverageSourcePathConfiguration());
            reportTask.executionData(configurations.createCoverageDataConfiguration());
        });
    }

}
