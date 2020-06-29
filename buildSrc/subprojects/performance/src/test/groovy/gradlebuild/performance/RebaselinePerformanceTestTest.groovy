package gradlebuild.performance

import gradlebuild.performance.tasks.RebaselinePerformanceTests
import spock.lang.Specification

class RebaselinePerformanceTestTest extends Specification {
    def "replaces the existing baseline with the specified one"() {
        expect:
        newContent == RebaselinePerformanceTests.rebaselineContent(oldContent, baseline)

        where:
        oldContent                                     | baseline                  | newContent
        'runner.targetVersions = ["4.5"]'              | "4.7"                     | 'runner.targetVersions = ["4.7"]'
        'targetVersions = ["4.5"]'                     | "4.7"                     | 'targetVersions = ["4.7"]'
        'targetVersions = ["4.5"]'                     | "4.7-20180320095059+0000" | 'targetVersions = ["4.7-20180320095059+0000"]'
        'targetVersions = ["4.7-20180320095059+0000"]' | "4.7"                     | 'targetVersions = ["4.7"]'
        'targetVersions = ["4.5", "4.6"]'              | "4.7"                     | 'targetVersions = ["4.7"]'
    }
}
