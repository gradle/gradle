package org.gradle.api.plugins.quality

import org.gradle.api.Project

class GroovyCodeQualityPluginConvention {
    String codeNarcConfigFileName
    String codeNarcReportsDirName
    private final Project project

    def GroovyCodeQualityPluginConvention(Project project) {
        this.project = project
        codeNarcConfigFileName = 'config/codenarc/codenarc.xml'
        codeNarcReportsDirName = 'codenarc'
    }

    File getCodeNarcConfigFile() {
        project.file(codeNarcConfigFileName)
    }

    File getCodeNarcReportsDir() {
        new File(project.reportsDir, codeNarcReportsDirName)
    }
}
