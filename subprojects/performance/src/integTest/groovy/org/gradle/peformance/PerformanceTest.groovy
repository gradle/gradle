package org.gradle.peformance

import spock.lang.Specification
import org.gradle.integtests.fixtures.*

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    def "current release is not worse than the previous one"() {
        when:
        //actually, fails only at 16m
        def previousExecuter = executer(previous, "small").withGradleOpts("-Xmx20m")
        def previousResult = measure {
            previousExecuter.run()
        }

        def currentExecuter = executer(current, "small").withGradleOpts("-Xmx20m")
        def currentResult = measure {
            currentExecuter.run()
        }

        then:
        previousResult.exception == null & currentResult.exception == null
        previousResult.executionTime <= currentResult.executionTime
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
    
    class MeasuredResult {
        long executionTime
        Exception exception
    }

    MeasuredResult measure(Closure operation) {
        long before = System.currentTimeMillis()
        def out = new MeasuredResult()
        try {
            operation()
        } catch (Exception e) {
            out.exception = e
        }
        out.executionTime = System.currentTimeMillis() - before
        return out
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
