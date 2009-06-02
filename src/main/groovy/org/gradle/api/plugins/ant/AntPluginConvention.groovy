package org.gradle.api.plugins.ant

import org.gradle.api.Project
import org.apache.tools.ant.ProjectHelper
import org.apache.tools.ant.Target

public class AntPluginConvention {
    private final Project project

    def AntPluginConvention(Project project) {
        this.project = project
    }

    def importAntBuild(String file) {
        importAntBuild(project.file(file))
    }
    
    def importAntBuild(File file) {
        org.apache.tools.ant.Project antProject = project.ant.project
        antProject.basedir = file.parentFile
        ProjectHelper.configureProject(antProject, file)
        
        antProject.targets.each {name, Target target ->
            if (target.name) {
                AntTask task = project.tasks.add(target.name, AntTask)
                task.target = target
            }
        }
    }
}