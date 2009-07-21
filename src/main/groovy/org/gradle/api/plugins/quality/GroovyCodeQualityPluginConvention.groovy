package org.gradle.api.plugins.quality

import org.gradle.api.Project

class GroovyCodeQualityPluginConvention {
    String codeNarcReportFileName
    String codeNarcTestReportFileName
    private final Project project

    def GroovyCodeQualityPluginConvention(Project project) {
        this.project = project
        codeNarcReportFileName = 'codenarc/main.html'
        codeNarcTestReportFileName = 'codenarc/test.html'
    }

    File getCodeNarcReportFile() {
        new File(project.reportsDir, codeNarcReportFileName)
    }

    File getCodeNarcTestReportFile() {
        new File(project.reportsDir, codeNarcTestReportFileName)
    }
}
