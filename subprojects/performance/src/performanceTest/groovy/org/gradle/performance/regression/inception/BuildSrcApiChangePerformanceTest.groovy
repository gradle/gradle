/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.performance.regression.inception

import org.gradle.api.JavaVersion
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.mutator.ApplyAbiChangeToGroovySourceFileMutator
import org.gradle.performance.mutator.ApplyNonAbiChangeToGroovySourceFileMutator
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX],
        testProjects = ["mediumMonolithicJavaProject", "largeJavaMultiProject", "largeJavaMultiProjectKotlinDsl"])
)
class BuildSrcApiChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        def targetVersion = "6.8-20201120002131+0000"
        runner.targetVersions = [targetVersion]
        runner.minimumBaseVersion = "6.8"
        runner.warmUpRuns = 3
    }

    def setupGradleOpts() {
        useG1GarbageCollectorOnJava8(runner)
    }

    def "buildSrc abi change"() {
        given:
        setupGradleOpts()
        runner.tasksToRun = ['help']
        runner.runs = determineNumberOfRuns(runner.testProject)

        and:
        def changingClassFilePath = "buildSrc/src/main/groovy/ChangingClass.groovy"
        runner.addBuildMutator { new CreateChangingClassMutator(it, changingClassFilePath) }
        runner.addBuildMutator { new ApplyAbiChangeToGroovySourceFileMutator(new File(it.projectDir, changingClassFilePath)) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "buildSrc non-abi change"() {
        given:
        setupGradleOpts()
        runner.tasksToRun = ['help']
        runner.runs = determineNumberOfRuns(runner.testProject)

        and:
        def changingClassFilePath = "buildSrc/src/main/groovy/ChangingClass.groovy"
        runner.addBuildMutator { new CreateChangingClassMutator(it, changingClassFilePath) }
        runner.addBuildMutator { new ApplyNonAbiChangeToGroovySourceFileMutator(new File(it.projectDir, changingClassFilePath)) }

        when:
        def result = runner.run()
        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    private static int determineNumberOfRuns(String testProject) {
        switch (testProject) {
            case 'mediumMonolithicJavaProject':
                return 40
            case 'largeJavaMultiProject':
                return 20
            case 'largeJavaMultiProjectKotlinDsl':
                return 10
            default:
                20
        }
    }

    private static void useG1GarbageCollectorOnJava8(CrossVersionPerformanceTestRunner runner) {
        if (!JavaVersion.current().isJava9Compatible()) {
            runner.gradleOpts.addAll(['-XX:+UnlockExperimentalVMOptions', '-XX:+UseG1GC'])
        }
    }

    private static class CreateChangingClassMutator implements BuildMutator {

        CreateChangingClassMutator(InvocationSettings settings, String filePath) {
            new File(settings.projectDir, filePath).with {
                parentFile.mkdirs()
                // We need to create the file in the constructor, since the file change mutators read the text of the file in the constructor as well.
                // It would be better if the file change mutators would read the original test in `beforeScenario`, so we could create the file here
                // as well in beforeScenario.
                text = """
                    class ChangingClass {
                        void changingMethod() {
                            System.out.println("Do the thing");
                        }
                    }
                """
            }
        }
    }
}
