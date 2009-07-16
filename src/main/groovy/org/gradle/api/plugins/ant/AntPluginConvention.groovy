package org.gradle.api.plugins.ant

import org.gradle.api.Project
import org.apache.tools.ant.ProjectHelper
import org.apache.tools.ant.Target
import org.apache.tools.ant.MagicNames
import org.gradle.api.GradleException

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
        File baseDir = file.parentFile
        org.apache.tools.ant.Project antProject = project.ant.project

        Set existingAntTargets = new HashSet(antProject.targets.keySet())
        File oldBaseDir = antProject.baseDir
        antProject.baseDir = baseDir
        try {
            antProject.setUserProperty(MagicNames.ANT_FILE, file.getAbsolutePath())
            ProjectHelper.configureProject(antProject, file)
        } catch(Exception e) {
            throw new GradleException("Could not import Ant build file '$file'.", e)
        } finally {
            antProject.baseDir = oldBaseDir
        }

        // Chuck away the implicit target. It has already been executed
        antProject.targets.remove('')

        // Add an adapter for each newly added target
        Set newAntTargets = new HashSet(antProject.targets.keySet())
        newAntTargets.removeAll(existingAntTargets)
        newAntTargets.each {name ->
            Target target = antProject.targets[name]
            AntTarget task = project.tasks.add(target.name, AntTarget)
            task.target = target
            task.baseDir = baseDir
        }
    }
}