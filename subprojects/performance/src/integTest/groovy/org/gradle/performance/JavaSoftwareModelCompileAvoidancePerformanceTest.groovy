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

import org.apache.commons.io.FileUtils
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentRunner
import spock.lang.Unroll

import java.util.regex.Pattern

class JavaSoftwareModelCompileAvoidancePerformanceTest extends AbstractCrossBuildPerformanceTest {
    private static int perc(int perc, int total) {
        (int) Math.ceil(total * (double) perc / 100d)
    }

    @Unroll("CompileAvoidance '#testCompileAvoidance' measuring compile avoidance speed when #cardinalityDesc #scenario #apiDesc")
    def "build java software model project"() {
        given:
        runner.testGroup = "compile avoidance using Java software model"
        runner.testId = "$size project compile avoidance $apiDesc and $cardinalityDesc $scenario"
        runner.buildExperimentListener = new SourceFileUpdater(nonApiChanges, abiCompatibleChanges, abiBreakingChanges)

        runner.buildSpec {
            projectName(testCompileAvoidance).displayName(runner.testId).invocation {
                tasksToRun('assemble').useDaemon().gradleOpts('-Xmx2G')
            }
        }

        when:
        runner.run()

        then:
        noExceptionThrown()

        where:

        // nonApiChanges, abiCompatibleChanges and abiBreakingChanges are expressed in percentage of projects that are going to be
        // updated in the test
        scenario                 | testCompileAvoidance                         | nonApiChanges | abiCompatibleChanges | abiBreakingChanges
        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 10            | 0                    | 0
        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 10            | 0                    | 0
        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 50            | 0                    | 0
        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 50            | 0                    | 0
        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 10            | 0                    | 0
        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 10            | 0                    | 0
        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 50            | 0                    | 0
        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 50            | 0                    | 0

        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 10                   | 0
        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 10                   | 0
        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 50                   | 0
        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 50                   | 0
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 10                   | 0
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 10                   | 0
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 50                   | 0
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 50                   | 0

        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 10
        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 10
        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 50
        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 50
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 10
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 10
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 50
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 50

        size = testCompileAvoidance[0..4]
        cardinalityDesc = (nonApiChanges + abiCompatibleChanges + abiBreakingChanges) < 50 ? 'some' : 'many'
        apiDesc = testCompileAvoidance.contains('Without') ? 'without declared API' : 'with declared API'
    }

    private static class SourceFileUpdater extends BuildExperimentListenerAdapter {
        private List<File> projects
        private int projectCount

        private final Set<File> updatedFiles = []
        private final int nonApiChanges
        private final int abiCompatibleChanges
        private final int abiBreakingChanges

        SourceFileUpdater(int nonApiChanges, int abiCompatibleChanges, int abiBreakingChanges) {
            this.abiBreakingChanges = abiBreakingChanges
            this.nonApiChanges = nonApiChanges
            this.abiCompatibleChanges = abiCompatibleChanges
        }

        int nonApiChangesCount() {
            perc(nonApiChanges, projectCount)
        }

        int compatibleApiChangesCount() {
            perc(abiCompatibleChanges, projectCount)
        }

        int breakingApiChangesCount() {
            perc(abiBreakingChanges, projectCount)
        }

        @Override
        void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
            def projectDir = invocationInfo.projectDir
            if (projects == null) {

                projects = projectDir.listFiles().findAll { it.directory && it.name.startsWith('project') }.sort { it.name }
                projectCount = projects.size()

                // forcefully delete build directories (so that dirty local runs do not interfere with results)
                projects.each { pDir ->
                    FileUtils.deleteDirectory(new File(pDir, 'build'))
                }

                // make sure execution is consistent independently of time
                Collections.shuffle(projects, new Random(31 * projectCount))
                // we create an initial copy of all source files so that we are consistent accross builds even if interrupted
                def backups = []
                def sources = []
                projectDir.eachFileRecurse { file ->
                    if (file.name.endsWith('~')) {
                        backups << file
                    } else if (file.name.endsWith('.java')) {
                        sources << file
                    }
                }
                // restore files first
                backups.each { file ->
                    FileUtils.copyFile(file, new File(file.parentFile, file.name - '~'), true)
                }
                // then create a copy of each source file
                sources.each { file ->
                    FileUtils.copyFile(file, new File(file.parentFile, file.name + '~'), true)
                }
            }
            if (!updatedFiles.isEmpty()) {
                updatedFiles.each { File file ->
                    println "Reverting changes to $file"
                    FileUtils.copyFile(new File(file.parentFile, file.name + '~'), file, true)
                }
                updatedFiles.clear()
            } else if (invocationInfo.phase != BuildExperimentRunner.Phase.WARMUP) {
                projects.take(nonApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/internal'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating non-API source file $updatedFile"
                    updatedFiles << updatedFile
                    updatedFile.text = updatedFile.text.replace('private final String property;', '''
private final String property;
public String addedProperty;
''')
                }

                projects.take(compatibleApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating API source file $updatedFile in ABI compatible way"
                    updatedFiles << updatedFile
                    updatedFile.text = updatedFile.text.replace('return property;', 'return property.toUpperCase();')
                }

                projects.take(breakingApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating API source file $updatedFile in ABI breaking way"
                    updatedFiles << updatedFile
                    updatedFile.text = updatedFile.text.replace('one()', 'two()')
                    // need to locate all affected classes
                    def updatedClass = updatedFile.name - '.java'
                    projectDir.eachFileRecurse { f->
                        if (f.name.endsWith('.java')) {
                            def txt = f.text
                            if (txt.contains("${updatedClass}.one()")) {
                                updatedFiles << f
                                println "Updating consuming source $f"
                                f.text = txt.replaceAll(Pattern.quote("${updatedClass}.one()"), "${updatedClass}.two()")
                            }
                        }
                    }
                }
            }
        }

        private static File pickFirstJavaSource(File internalDir) {
            internalDir.listFiles().find { it.name.endsWith('.java') }
        }
    }
}
