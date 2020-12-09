/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
