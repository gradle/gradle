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

class JavaSoftwareModelCompileAvoidancePerformanceTest extends AbstractCrossBuildPerformanceTest {
    static int perc(int perc, int total) {
        (int) Math.ceil(total * (double) perc / 100d)
    }

    @Unroll("Project '#testProject' measuring compile avoidance speed when #cardinalityDesc #scenario")
    def "build java software model project"() {
        given:
        runner.testGroup = "compile avoidance using API declaration"
        runner.testId = "$size project compile avoidance with API $cardinalityDesc $scenario build"
        def nonApiChangesCount = { projectCount ->
            perc(nonApiChanges, projectCount)
        }
        def compatibleApiChangesCount = { projectCount ->
            perc(abiCompatibleChanges, projectCount)
        }
        def breakingApiChangesCount = { projectCount ->
            perc(abiBreakingChanges, projectCount)
        }
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            private List<File> projects
            private int projectCount

            private final Map<File, byte[]> backup = [:]

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
                if (!backup.isEmpty()) {
                    backup.each { File path, byte[] origContents ->
                        println "Reverting changes to $path"
                        path.withOutputStream { out -> out.write(origContents) }
                    }
                    backup.clear()
                } else if (invocationInfo.phase != BuildExperimentRunner.Phase.WARMUP) {
                    projects.take(nonApiChangesCount(projectCount)).each { subproject ->
                        def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/internal'.replace((char) '/', File.separatorChar))
                        def updatedFile = pickFirstJavaSource(internalDir)
                        println "Updating non-API source file $updatedFile"
                        backup[updatedFile] = updatedFile.bytes
                        updatedFile.text = updatedFile.text.replace('private final String property;', '''
private final String property;
public String addedProperty;
''')
                    }

                    projects.take(compatibleApiChangesCount(projectCount)).each { subproject ->
                        def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                        def updatedFile = pickFirstJavaSource(internalDir)
                        println "Updating API source file $updatedFile in ABI compatible way"
                        backup[updatedFile] = updatedFile.bytes
                        updatedFile.text = updatedFile.text.replace('return property;', 'return property.toUpperCase();')
                    }

                    projects.take(breakingApiChangesCount(projectCount)).each { subproject ->
                        def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                        def updatedFile = pickFirstJavaSource(internalDir)
                        println "Updating API source file $updatedFile in ABI breaking way"
                        backup[updatedFile] = updatedFile.bytes
                        updatedFile.text = updatedFile.text.replace('public String getProperty() {', '''public String getPropertyToUpper() {
   return property.toUpperCase();
}

public String getProperty() {''')
                    }
                }
            }

            private File pickFirstJavaSource(File internalDir) {
                internalDir.listFiles().find { it.name.endsWith('.java') }
            }
        }

        runner.buildSpec {
            projectName("${size}JavaSwModelProjectWithApi").displayName("$size project compile avoidance with API declaration").invocation {
                tasksToRun('assemble').useDaemon().gradleOpts('-Xmx2G')
            }.invocationCount(10)
        }

        when:
        runner.run()

        then:
        noExceptionThrown()

        where:

        // nonApiChanges, abiCompatibleChanges and abiBreakingChanges are expressed in percentage of projects that are going to be
        // updated in the test
        scenario                     | testProject                      | nonApiChanges | abiCompatibleChanges | abiBreakingChanges
        'internal API change made'   | "smallJavaSwModelProjectWithApi" | 10            | 0                    | 0
        'internal API change made'   | "smallJavaSwModelProjectWithApi" | 50            | 0                    | 0
        'internal API change made'   | "largeJavaSwModelProjectWithApi" | 10            | 0                    | 0
        'internal API change made'   | "largeJavaSwModelProjectWithApi" | 50            | 0                    | 0

        'ABI compatible change made' | "smallJavaSwModelProjectWithApi" | 0             | 10                   | 0
        'ABI compatible change made' | "smallJavaSwModelProjectWithApi" | 0             | 50                   | 0
        'ABI compatible change made' | "largeJavaSwModelProjectWithApi" | 0             | 10                   | 0
        'ABI compatible change made' | "largeJavaSwModelProjectWithApi" | 0             | 50                   | 0

        'ABI breaking change made'   | "smallJavaSwModelProjectWithApi" | 0             | 0                    | 10
        'ABI breaking change made'   | "smallJavaSwModelProjectWithApi" | 0             | 0                    | 50
        'ABI breaking change made'   | "largeJavaSwModelProjectWithApi" | 0             | 0                    | 10
        'ABI breaking change made'   | "largeJavaSwModelProjectWithApi" | 0             | 0                    | 50

        size = testProject[0..4]
        cardinalityDesc = (nonApiChanges + abiCompatibleChanges + abiBreakingChanges) < 50 ? 'some' : 'many'
    }
}
