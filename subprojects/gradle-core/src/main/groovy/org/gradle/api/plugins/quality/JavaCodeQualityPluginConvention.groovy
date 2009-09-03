package org.gradle.api.plugins.quality

import org.gradle.api.Project

class JavaCodeQualityPluginConvention {
    String checkstyleConfigFileName
    String checkstyleResultsDirName
    private Project project

    def JavaCodeQualityPluginConvention(Project project) {
        this.project = project
        checkstyleConfigFileName = 'config/checkstyle/checkstyle.xml'
        checkstyleResultsDirName = 'checkstyle'
    }

    File getCheckstyleConfigFile() {
        project.file(checkstyleConfigFileName)
    }

    File getCheckstyleResultsDir() {
        new File(project.buildDir, checkstyleResultsDirName)
    }
}
