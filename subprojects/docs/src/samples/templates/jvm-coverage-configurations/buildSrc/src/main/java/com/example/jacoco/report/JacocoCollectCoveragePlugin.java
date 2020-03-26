package com.example.jacoco.report;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.io.File;

public class JacocoCollectCoveragePlugin implements Plugin<Project> {

    private Configuration coverageSourcePathConfiguration;

    @Override
    public void apply(Project project) {
        applyJacocoPlugin(project);
        disableSingleReport(project);
        createConfigurations(project);
        configureTestToPublishCoverageData(project);
        publishSourceSetsForCoverage(project);
    }

    private void publishSourceSetsForCoverage(Project project) {
        final JavaPluginConvention plugin = project.getConvention().getPlugin(JavaPluginConvention.class);
        final ConfigurationPublications outgoing = coverageSourcePathConfiguration.getOutgoing();
        plugin.getSourceSets().findByName("main").getAllJava().forEach(outgoing::artifact);
        plugin.getSourceSets().findByName("test").getAllJava().forEach(outgoing::artifact);
    }

    private void configureTestToPublishCoverageData(Project project) {
        final Task testTask = project.getTasks().getByName("test");
        final File destinationFile = testTask.getExtensions().findByType(JacocoTaskExtension.class).getDestinationFile();
        project.getArtifacts().add("coverageData", destinationFile, a -> a.builtBy(testTask));
    }

    private void createConfigurations(Project project) {
        final JacocoReportConfigurations jacocoConfigurations = JacocoReportConfigurations.forProducers(project);
        jacocoConfigurations.createRootConfiguration();
        jacocoConfigurations.createCoverageDataConfiguration();
        coverageSourcePathConfiguration = jacocoConfigurations.createCoverageSourcePathConfiguration();
    }

    private void applyJacocoPlugin(Project project) {
        project.getPluginManager().apply(JacocoPlugin.class);
    }

    private void disableSingleReport(Project project) {
        project.getTasks().withType(JacocoReport.class).forEach(t -> t.setEnabled(false));
    }

}
