package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.Set;

/**
 * The conventional configuration for the `ProjectReportsPlugin`.
 */
public class ProjectReportsPluginConvention {
    private String projectReportDirName = "project";
    private final Project project;

    public ProjectReportsPluginConvention(Project project) {
        this.project = project;
    }

    /**
     * The name of the directory to generate the project reports into, relative to the project's reports dir.
     */
    public String getProjectReportDirName() {
        return projectReportDirName;
    }

    public void setProjectReportDirName(String projectReportDirName) {
        this.projectReportDirName = projectReportDirName;
    }

    /**
     * Returns the directory to generate the project reports into.
     */
    public File getProjectReportDir() {
        return project.getExtensions().getByType(ReportingExtension.class).file(projectReportDirName);
    }

    public Set<Project> getProjects() {
        return WrapUtil.toSet(project);
    }
}
