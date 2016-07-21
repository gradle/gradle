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

import java.util.regex.Pattern

class JavaSoftwareModelSourceFileUpdater extends BaseJavalSourceFileUpdater {
    private final int nonApiChanges
    private final int abiCompatibleChanges
    private final int abiBreakingChanges

    JavaSoftwareModelSourceFileUpdater(int nonApiChanges, int abiCompatibleChanges, int abiBreakingChanges, SourceUpdateCardinality cardinality = SourceUpdateCardinality.ONE_FILE) {
        super(cardinality)
        this.abiBreakingChanges = abiBreakingChanges
        this.nonApiChanges = nonApiChanges
        this.abiCompatibleChanges = abiCompatibleChanges
    }

    private int nonApiChangesCount() {
        perc(nonApiChanges, projectsWithDependencies.size())
    }

    private int abiCompatibleApiChangesCount() {
        perc(abiCompatibleChanges, projectsWithDependencies.size())
    }

    private int abiBreakingApiChangesCount() {
        perc(abiBreakingChanges, projectsWithDependencies.size())
    }

    @Override
    protected void updateFiles() {
        projectsWithDependencies.take(nonApiChangesCount()).each { subproject ->
            def internalDir = new File(subproject, 'src/main/java/org/gradle/test/performance/internal'.replace((char) '/', File.separatorChar))
            cardinality.onSourceFile(internalDir, '.java') { updatedFile ->
                println "Updating non-API source file $updatedFile"
                Set<Integer> dependents = affectedProjects(subproject)
                createBackupFor(updatedFile)
                updatedFile.text = updatedFile.text.replace('private final String property;', '''
private final String property;
public String addedProperty;
''')
            }
        }

        projectsWithDependencies.take(abiCompatibleApiChangesCount()).each { subproject ->
            def srcDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
            cardinality.onSourceFile(srcDir, '.java') { updatedFile ->
                println "Updating API source file $updatedFile in ABI compatible way"
                Set<Integer> dependents = affectedProjects(subproject)
                createBackupFor(updatedFile)
                updatedFile.text = updatedFile.text.replace('return property;', 'return property.toUpperCase();')
            }
        }

        projectsWithDependencies.take(abiBreakingApiChangesCount()).each { subproject ->
            def srcDir = new File(subproject, 'src/main/java/org/gradle/test/performance/'.replace((char) '/', File.separatorChar))
            cardinality.onSourceFile(srcDir, '.java') { updatedFile ->
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
}
