package org.gradle.api.plugins.quality

import org.gradle.api.Project

class GroovyCodeQualityPluginConvention {
    String codeNarcConfigFileName
    String codeNarcTestConfigFileName
    String codeNarcReportFileName
    String codeNarcTestReportFileName
    private final Project project

    def GroovyCodeQualityPluginConvention(Project project) {
        this.project = project
        codeNarcConfigFileName = 'config/codenarc.xml'
        codeNarcReportFileName = 'codenarc/main.html'
        codeNarcTestReportFileName = 'codenarc/test.html'
    }

    File getCodeNarcConfigFile() {
        project.file(codeNarcConfigFileName)
    }

    File getCodeNarcTestConfigFile() {
        codeNarcTestConfigFileName ? project.file(codeNarcTestConfigFileName) : codeNarcConfigFile
    }
    
    File getCodeNarcReportFile() {
        new File(project.reportsDir, codeNarcReportFileName)
    }

    File getCodeNarcTestReportFile() {
        new File(project.reportsDir, codeNarcTestReportFileName)
    }
}
