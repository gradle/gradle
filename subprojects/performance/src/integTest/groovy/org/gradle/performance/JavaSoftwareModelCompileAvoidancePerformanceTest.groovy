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
//        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 50            | 0                    | 0
//        'internal API changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 50            | 0                    | 0
//
        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 10                   | 0
        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 10                   | 0
//        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 50                   | 0
//        'ABI compatible changes' | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 50                   | 0

        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 10
        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 10
//        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 50
//        'ABI breaking changes'   | "smallJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 50

        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 10            | 0                    | 0
        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 10            | 0                    | 0
//        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 50            | 0                    | 0
//        'internal API changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 50            | 0                    | 0
//
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 10                   | 0
        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 10                   | 0
//        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 50                   | 0
//        'ABI compatible changes' | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 50                   | 0
//
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 10
        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 10
//        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithApi"    | 0             | 0                    | 50
//        'ABI breaking changes'   | "largeJavaSwModelCompileAvoidanceWithoutApi" | 0             | 0                    | 50

        size = testCompileAvoidance[0..4]
        cardinalityDesc = (nonApiChanges + abiCompatibleChanges + abiBreakingChanges) < 50 ? 'some' : 'many'
        apiDesc = testCompileAvoidance.contains('Without') ? 'without declared API' : 'with declared API'
    }

    private static class SourceFileUpdater extends BuildExperimentListenerAdapter {
        private List<File> projects
        private List<File> projectsWithDependencies
        private int projectCount
        private Map<Integer, List<Integer>> dependencies
        private Map<Integer, List<Integer>> reverseDependencies

        private final Set<File> updatedFiles = []
        private final int nonApiChanges
        private final int abiCompatibleChanges
        private final int abiBreakingChanges

        SourceFileUpdater(int nonApiChanges, int abiCompatibleChanges, int abiBreakingChanges) {
            this.abiBreakingChanges = abiBreakingChanges
            this.nonApiChanges = nonApiChanges
            this.abiCompatibleChanges = abiCompatibleChanges
        }

        private static int perc(int perc, int total) {
            (int) Math.ceil(total * (double) perc / 100d)
        }

        private int nonApiChangesCount() {
            perc(nonApiChanges, projectsWithDependencies.size())
        }

        private int compatibleApiChangesCount() {
            perc(abiCompatibleChanges, projectsWithDependencies.size())
        }

        private int breakingApiChangesCount() {
            perc(abiBreakingChanges, projectsWithDependencies.size())
        }

        private File backupFileFor(File file) {
            new File(file.parentFile, "${file.name}~")
        }

        private void createBackupFor(File file) {
            updatedFiles << file
            FileUtils.copyFile(file, backupFileFor(file), true)
        }

        private void restoreFiles() {
            updatedFiles.each { File file ->
                restoreFile(file)
            }
            updatedFiles.clear()
        }

        private void restoreFile(File file) {
            println "Restoring $file"
            def backup = backupFileFor(file)
            FileUtils.copyFile(backup, file, true)
            backup.delete()
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
                // restore stale backup files in case a build was interrupted even if interrupted
                projectDir.eachFileRecurse { file ->
                    if (name.endsWith('~')) {
                        restoreFile(new File(file.parentFile, file.name - '~'))
                    }
                }

                // retrieve the dependencies in an exploitable form
                dependencies = new GroovyShell().evaluate(new File(projectDir, 'generated-deps.groovy'))
                reverseDependencies = [:].withDefault { [] }
                dependencies.each { p, deps ->
                    deps.each {
                        reverseDependencies[it] << p
                    }
                }
                projectsWithDependencies = projects.findAll { File it ->
                    reverseDependencies[projectId(it)]
                }
            }
            if (!updatedFiles.isEmpty()) {
                restoreFiles()
            } else if (invocationInfo.phase != BuildExperimentRunner.Phase.WARMUP) {
                projectsWithDependencies.take(nonApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/internal'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating non-API source file $updatedFile"
                    Set<Integer> dependents = affectedProjects(subproject)
                    createBackupFor(updatedFile)
                    updatedFile.text = updatedFile.text.replace('private final String property;', '''
private final String property;
public String addedProperty;
''')
                }

                projectsWithDependencies.take(compatibleApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating API source file $updatedFile in ABI compatible way"
                    Set<Integer> dependents = affectedProjects(subproject)
                    createBackupFor(updatedFile)
                    updatedFile.text = updatedFile.text.replace('return property;', 'return property.toUpperCase();')
                }

                projectsWithDependencies.take(breakingApiChangesCount()).each { subproject ->
                    def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
                    def updatedFile = pickFirstJavaSource(internalDir)
                    println "Updating API source file $updatedFile in ABI breaking way"
                    createBackupFor(updatedFile)
                    updatedFile.text = updatedFile.text.replace('one() {', 'two() {')
                    // need to locate all affected classes
                    def updatedClass = updatedFile.name - '.java'
                    affectedProjects(subproject).each {
                        def subDir = new File(projectDir, "project$it")
                        if (subDir.exists()) {
                            // need to check for existence because dependency
                            // generation strategy may be generating dependencies
                            // outside what is really declared
                            subDir.eachFileRecurse { f ->
                                if (f.name.endsWith('.java')) {
                                    def txt = f.text
                                    if (txt.contains("${updatedClass}.one()")) {
                                        createBackupFor(f)
                                        println "Updating consuming source $f"
                                        f.text = txt.replaceAll(Pattern.quote("${updatedClass}.one()"), "${updatedClass}.two()")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private Set<Integer> affectedProjects(File subproject) {
            Set dependents = reverseDependencies[projectId(subproject)] as Set
            Set transitiveClosure = new HashSet<>(dependents)
            int size = -1
            while (size != transitiveClosure.size()) {
                size = transitiveClosure.size()
                def newDeps = []
                transitiveClosure.each {
                    newDeps.addAll(reverseDependencies[it])
                }
                transitiveClosure.addAll(newDeps)
            }
            println "Changes will transitively affect projects ${transitiveClosure.join(' ')}"
            dependents
        }

        private int projectId(File it) {
            Integer.valueOf(it.name - 'project')
        }

        private static File pickFirstJavaSource(File internalDir) {
            internalDir.listFiles().find { it.name.endsWith('.java') }
        }
    }
}
