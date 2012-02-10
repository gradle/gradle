package org.gradle.peformance

import spock.lang.Specification
import org.gradle.integtests.fixtures.*

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def "small project"() {
        given:
        def GradleDistribution current = new GradleDistribution()
        def previous = new ReleasedVersions(current).last

        def projectDir = findProjectDir("small")

        when:
        def previousExecuter = executer(previous, projectDir)
        int previousResult = executionTime {
            previousExecuter.run()
        }

        def projectDir2 = findProjectDir("small")

        def currentExecuter = executer(current, projectDir2)
        int currentResult = executionTime {
            currentExecuter.run()
        }

        then:
        previousResult <= currentResult
    }

    private GradleExecuter executer(BasicGradleDistribution dist, File projectDir) {
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
        def projectDir = new File("subprojects/performance/build/$name").absoluteFile
        if (!projectDir.isDirectory()) {
            def message = "Looks like the sample '$name' was not generated.\n" +
                "I've tried to find it at: $projectDir\n" +
                "Please run 'gradlew performance:$name' to generate the sample."
            assert false: message
        }
        projectDir
    }
}
