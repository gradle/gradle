/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.BRPPerformanceTest
import org.junit.experimental.categories.Category

@Category(BRPPerformanceTest)
class BuildReceiptPluginPerformanceTest extends AbstractCrossBuildPerformanceTest {

    def "build receipt plugin comparison"() {
        given:
        runner.testGroup = "build receipt plugin"
        runner.testId = "compare with vs new without build receipt plugin build"
        def opts = ["-Dreceipt", "-Dreceipt.dump"]
        def tasks = ['clean', 'build']

        runner.baseline {
            projectName("largeJavaSwModelProjectWithoutBuildReceipts").displayName("with plugin").invocation {
                gradleOpts(*opts)
                tasksToRun(*tasks).useDaemon()
            }
        }

        runner.buildSpec {
            projectName("largeJavaSwModelProjectWithBuildReceipts").displayName("without plugin").invocation {
                gradleOpts(*opts)
                tasksToRun(*tasks).useDaemon()
            }
        }

        when:
        runner.run()

        then:
        noExceptionThrown()
    }

}
