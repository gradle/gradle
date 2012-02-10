package org.gradle.peformance

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.peformance.fixture.PerformanceTestRunner
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    def "current release is not slower than the previous one"() {
        when:
        def results = new PerformanceTestRunner(testProject: "small", runs: 5, warmUpRuns: 1).run()
        
        then:
        results.assertEveryBuildSucceeds()
        results.assertCurrentReleaseIsNotSlower()
    }

    def "current release requires not more memory than the previous one"() {
        when:
        def results = new PerformanceTestRunner(testProject: "small", runs: 1, gradleOpts: ['-Xmx17m']).run()

        then:
        results.assertEveryBuildSucceeds()
    }
}