/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.experiment.java

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category

@Category(PerformanceExperiment)
class GradleFeaturePreviewsPerformanceTest extends AbstractCrossBuildPerformanceTest {
    private final static EXCLUDE_RULE_MERGING_TEST_PROJECT = 'excludeRuleMergingBuild'

    def "resolveDependencies on exclude merging project with improved pom support"() {
        def memory = '1g'

        when:
        runner.testGroup = "feature previews"
        runner.buildSpec {
            projectName(EXCLUDE_RULE_MERGING_TEST_PROJECT).displayName("advanced-pom-support").invocation {
                tasksToRun("resolveDependencies").args('-PimprovedPomSupport=true').gradleOpts("-Xms${memory}", "-Xmx${memory}")
            }
        }
        runner.baseline {
            projectName(EXCLUDE_RULE_MERGING_TEST_PROJECT).displayName("no-advanced-pom-support").invocation {
                tasksToRun("resolveDependencies").args('-PimprovedPomSupport=false').gradleOpts("-Xms${memory}", "-Xmx${memory}")
            }
        }

        then:
        runner.run()
    }

    def "resolveDependencies on large number of dependencies with improved pom support"() {
        def memory = '1g'

        when:
        runner.testGroup = "feature previews"
        runner.buildSpec {
            projectName(EXCLUDE_RULE_MERGING_TEST_PROJECT).displayName("advanced-pom-support").invocation {
                tasksToRun("resolveDependencies").args('-PimprovedPomSupport=true', '-PnoExcludes').gradleOpts("-Xms${memory}", "-Xmx${memory}")
            }
        }
        runner.baseline {
            projectName(EXCLUDE_RULE_MERGING_TEST_PROJECT).displayName("no-advanced-pom-support").invocation {
                tasksToRun("resolveDependencies").args('-PimprovedPomSupport=false', '-PnoExcludes').gradleOpts("-Xms${memory}", "-Xmx${memory}")
            }
        }

        then:
        runner.run()
    }

}
