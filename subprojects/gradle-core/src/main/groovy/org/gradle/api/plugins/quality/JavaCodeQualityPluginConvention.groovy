package org.gradle.api.plugins.quality

import org.gradle.api.Project

class JavaCodeQualityPluginConvention {
    String checkstyleConfigFileName
    String checkstyleTestConfigFileName
    String checkstyleResultFileName
    String checkstyleTestResultFileName
    private Project project

    def JavaCodeQualityPluginConvention(Project project) {
        this.project = project
        checkstyleConfigFileName = 'config/checkstyle.xml'
        checkstyleResultFileName = 'checkstyle/main.xml'
        checkstyleTestResultFileName = 'checkstyle/test.xml'
    }

    File getCheckstyleConfigFile() {
        project.file(checkstyleConfigFileName)
    }

    File getCheckstyleTestConfigFile() {
        checkstyleTestConfigFileName ? project.file(checkstyleTestConfigFileName) : checkstyleConfigFile
    }

    File getCheckstyleResultFile() {
        new File(project.buildDir, checkstyleResultFileName)
    }
    
    File getCheckstyleTestResultFile() {
        new File(project.buildDir, checkstyleTestResultFileName)
    }
}
