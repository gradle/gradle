package org.gradle.peformance

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.peformance.fixture.PerformanceTestRunner
import spock.lang.Specification
import spock.lang.Unroll

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    @Unroll({"Project '$testProject' ran $runs times. Current release is not slower than the previous one."})
    def "speed"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject, runs: runs, warmUpRuns: 1).run()
        result.assertCurrentReleaseIsNotSlower()

        where:
        testProject | runs
        "small"     | 10
        "multi"     | 10
    }

    @Unroll({"Project '$testProject' with heap size: $heapSize. Current release does not require more memory than the previous one."})
    def "memory"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject, runs: 1, gradleOpts: [heapSize]).run()
        result.assertEveryBuildSucceeds()

        where:
        testProject | heapSize
        "small"     | '-Xmx17m' //fails with 16m
        "multi"     | '-Xmx56m' //fails with 54m
    }
}