package org.gradle.api.plugins;

import org.gradle.api.Project;

import java.io.File;

public class WarPluginConvention {
    private String webAppDirName;
    private final Project project;

    public WarPluginConvention(Project project) {
        this.project = project;
        webAppDirName = "src/main/webapp";
    }

    /**
     * Returns the web application directory.
     */
    public File getWebAppDir() {
        return project.file(webAppDirName);
    }

    /**
     * The name of the web application directory, relative to the project directory.
     */
    public String getWebAppDirName() {
        return webAppDirName;
    }

    public void setWebAppDirName(String webAppDirName) {
        this.webAppDirName = webAppDirName;
    }

    public final Project getProject() {
        return project;
    }
}
