package org.gradle.peformance

import spock.lang.Specification
import org.gradle.tooling.GradleConnector

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def "small project"() {
        given:
        def projectDir = findProjectDir("small")

        when:
        int m6result = executionTime {
            def connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .searchUpwards(false)
                .useGradleVersion("1.0-milestone-6")
                .connect()

            connection.newBuild().forTasks('clean', 'build').run()
        }

        int m7result = executionTime {
            def connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .searchUpwards(false)
                .useGradleVersion("1.0-milestone-7")
                .connect()

            connection.newBuild().forTasks('clean', 'build').run()
        }

        then:
        m7result <= m6result
    }

    long executionTime(Closure operation) {
        long before = System.currentTimeMillis()
        operation()
        return System.currentTimeMillis() - before
    }

    File findProjectDir(String name) {
        def projectDir = new File("build/small").absoluteFile
        if (!projectDir.isDirectory()) {
            def message = "Looks like the sample '$name' was not generated.\n" +
                "I've tried to find it at: $projectDir\n" +
                "Please run 'gradle $name' to generate the sample."
            assert false: message
        }
        projectDir
    }
}
