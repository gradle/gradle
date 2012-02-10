package org.gradle.peformance

import spock.lang.Specification
import org.gradle.integtests.fixtures.*

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    def "small project"() {
        when:
        def previousExecuter = executer(previous, "small")
        int previousResult = executionTime {
            previousExecuter.run()
        }

        def currentExecuter = executer(current, "small")
        int currentResult = executionTime {
            currentExecuter.run()
        }

        then:
        previousResult <= currentResult
    }

    private GradleExecuter executer(BasicGradleDistribution dist, String testProjectName) {
        def projectDir = findProjectDir(testProjectName)
        def executer
        if (dist instanceof GradleDistribution) {
            executer = new GradleDistributionExecuter(GradleDistributionExecuter.Executer.forking, dist)
        } else {
            executer = dist.executer()
        }
        return executer.withArguments('-u').inDirectory(projectDir).withTasks('clean', 'build')
    }

    long executionTime(Closure operation) {
        long before = System.currentTimeMillis()
        operation()
        return System.currentTimeMillis() - before
    }

    File findProjectDir(String name) {
        def base = "subprojects/performance/build"
        def locations = ["$base/$name", "../../$base/$name"]
        def dirs = locations.collect { new File(it).absoluteFile }
        for (File dir: dirs) {
            if (dir.isDirectory()) {
                return dir
            }
        }
        def message = "Looks like the test project '$name' was not generated.\nI've tried to find it at:\n"
        dirs.each { message += "  $it\n" }
        message +="Please run 'gradlew performance:$name' to generate the test project."
        assert false: message
    }
}
