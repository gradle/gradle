package org.gradle.api.plugins.ant

import org.gradle.api.Project
import org.apache.tools.ant.ProjectHelper
import org.apache.tools.ant.Target
import org.apache.tools.ant.MagicNames

public class AntPluginConvention {
    private final Project project

    def AntPluginConvention(Project project) {
        this.project = project
    }

    def importAntBuild(String file) {
        importAntBuild(project.file(file))
    }

    def importAntBuild(File file) {
        file = file.canonicalFile
        org.apache.tools.ant.Project antProject = project.ant.project
        File oldBaseDir = antProject.baseDir
        antProject.baseDir = file.parentFile

        try {
            antProject.setUserProperty(MagicNames.ANT_FILE, file.getAbsolutePath())
            ProjectHelper.configureProject(antProject, file)
        } finally {
            antProject.baseDir = oldBaseDir
        }

        antProject.targets.each {name, Target target ->
            if (target.name) {
                AntTarget task = project.tasks.add(target.name, AntTarget)
                task.target = target
            }
        }
    }
}