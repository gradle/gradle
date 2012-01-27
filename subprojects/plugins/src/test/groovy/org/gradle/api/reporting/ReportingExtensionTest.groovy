package org.gradle.api.reporting

import spock.lang.Specification
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project

class ReportingExtensionTest extends Specification {
    
    Project project = ProjectBuilder.builder().build()
    ReportingExtension extension = new ReportingExtension(project)
    
    def "defaults to reports dir in build dir"() {
        expect:
        extension.baseDir == new File(project.buildDir, ReportingExtension.DEFAULT_REPORTS_DIR_NAME)

        when:
        project.buildDir = project.file("newBuildDir")

        then:
        extension.baseDir == new File(project.buildDir, ReportingExtension.DEFAULT_REPORTS_DIR_NAME)
    }
    
    def "reports dir can be changed lazily"() {
        given:
        def dir = "a"

        when:
        extension.baseDir = { dir }

        then:
        extension.baseDir == project.file("a")

        when:
        dir = "b"

        then:
        extension.baseDir == project.file("b")

    }
}
