// tag::init-test[]
package org.example

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import spock.lang.Specification

// end::init-test[]
/*
// tag::init-test[]
class PluginTutorialPluginTest extends Specification {
    def "plugin registers task"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply("org.example.greeting")

        then:
        project.tasks.findByName("greeting") != null
    }
}
// end::init-test[]
*/