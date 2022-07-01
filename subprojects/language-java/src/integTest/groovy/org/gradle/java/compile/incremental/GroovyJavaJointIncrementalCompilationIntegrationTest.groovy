/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompiledLanguage

class GroovyJavaJointIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {
    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    private static final Map SOURCES = [
        'G': 'class G { }',
        'G.changed': 'class G { int i }',
        'G.failure': 'class G { garbage }',
        'J': 'class J { }',
        'J.changed': 'class J { int i; }',
        'J.failure': 'class J { garbage }',

        'G_J': 'class G_J extends J { }',

        'J_G': 'class J_G extends G { }',

        'G_G': 'class G_G extends G { }',
        'J_G_G': 'class J_G_G extends G_G { }',
    ]

    def 'Groovy-Java joint compilation on #scenario'() {
        given:
        applyGroovyFileSet(initialSet)
        run "compileGroovy"

        when:
        applyGroovyFileSet(firstChange)
        run "compileGroovy", "--info"

        then: 'first build'
        upToDateOrMessage(firstBuildMessage)

        when: 'second build'
        applyGroovyFileSet(secondChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage(secondBuildMessage)

        where:
        scenario                                    | initialSet            | firstChange                   | firstBuildMessage            | secondChange                  | secondBuildMessage
        'Add Java files to Groovy file set'         | ['G']                 | ['G', 'J']                    | 'Incremental compilation of' | ['G', 'J']                    | 'UP-TO-DATE'
        'Add Groovy files to Java file set'         | ['J']                 | ['G', 'J']                    | 'Incremental compilation of' | ['G', 'J.changed']            | 'Incremental compilation of'
        'Change Java files in joint compilation'    | ['G', 'J']            | ['G', 'J.changed']            | 'Incremental compilation of' | ['G', 'J.changed']            | 'UP-TO-DATE'
        'Change Groovy files which Java depends on' | ['G', 'J_G']          | ['G.changed', 'J_G']          | 'Incremental compilation of' | ['G.changed', 'J_G']          | 'UP-TO-DATE'
        'Remove Java files in joint compilation'    | ['G', 'J']            | ['G']                         | 'UP-TO-DATE'                 | ['G', 'G_G']                  | 'Incremental compilation of '
        'Remove Groovy files in joint compilation'  | ['G', 'J']            | ['J']                         | 'UP-TO-DATE'                 | ['J', 'G']                    | 'Incremental compilation of'
        'Add Groovy files to joint file set'        | ['G', 'J']            | ['G', 'J', 'G_G']             | 'Incremental compilation of' | ['G', 'J', 'G_G']             | 'UP-TO-DATE'
        'Change root Groovy files '                 | ['G', 'G_G', 'J_G_G'] | ['G.changed', 'G_G', 'J_G_G'] | 'Incremental compilation of' | ['G.changed', 'G_G', 'J_G_G'] | 'UP-TO-DATE'
    }

    def 'Groovy-Java joint compilation incremental compilation after failure: #scenario'() {
        given:
        applyGroovyFileSet(initialSet)
        outputs.snapshot { run "compileGroovy" }

        when: 'first build'
        applyGroovyFileSet(firstChange)
        runAndFail "compileGroovy", "--info"

        then:
        outputs.noneRecompiled()

        when: 'second build'
        applyGroovyFileSet(secondChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage(secondBuildMessage)
        outputs.recompiledClasses(secondChangeRecompiledClasses as String[])

        when: 'third build'
        outputs.snapshot()
        applyGroovyFileSet(thirdChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage('UP-TO-DATE')
        outputs.noneRecompiled()

        where:
        scenario                                    | initialSet   | firstChange          | secondChange         | secondChangeRecompiledClasses | secondBuildMessage           | thirdChange
        'Change Java files'                         | ['G', 'J']   | ['G', 'J.failure']   | ['G', 'J.changed']   | ['J']                         | 'Incremental compilation of' | ['G', 'J.changed']
        'Change Groovy files'                       | ['G', 'J']   | ['G.failure', 'J']   | ['G.changed', 'J']   | ['G']                         | 'Incremental compilation of' | ['G.changed', 'J']
        'Change Java files which Groovy depends on' | ['G_J', 'J'] | ['G_J', 'J.failure'] | ['G_J', 'J.changed'] | ['G_J', 'J']                  | 'Incremental compilation of' | ['G_J', 'J.changed']
        'Change Groovy files which Java depends on' | ['G', 'J_G'] | ['G.failure', 'J_G'] | ['G.changed', 'J_G'] | ['G', 'J_G']                  | 'Incremental compilation of' | ['G.changed', 'J_G']
    }

    def 'Groovy-Java joint compilation incremental compilation after failure with mix sources: #scenario'() {
        given:
        applyMixFileSet(initialSet)
        outputs.snapshot { run "compileGroovy" }

        when: 'first build'
        applyMixFileSet(firstChange)
        runAndFail "compileGroovy", "--info"

        then:
        outputs.noneRecompiled()

        when: 'second build'
        applyMixFileSet(secondChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage(":compileJava", secondBuildMessage[0] as String)
        upToDateOrMessage(":compileGroovy", secondBuildMessage[1] as String)
        outputs.recompiledClasses(secondChangeRecompiledClasses as String[])

        when: 'third build'
        outputs.snapshot()
        applyMixFileSet(thirdChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage(":compileJava", 'UP-TO-DATE')
        upToDateOrMessage(":compileGroovy", 'UP-TO-DATE')
        outputs.noneRecompiled()

        where:
        scenario                                    | initialSet   | firstChange          | secondChange         | secondChangeRecompiledClasses | secondBuildMessage                                           | thirdChange
        'Change Java files'                         | ['G', 'J']   | ['G', 'J.failure']   | ['G', 'J.changed']   | ['J']                         | ['Incremental compilation of', 'UP-TO-DATE']                 | ['G', 'J.changed']
        'Change Java files which Groovy depends on' | ['G_J', 'J'] | ['G_J', 'J.failure'] | ['G_J', 'J.changed'] | ['G_J', 'J']                  | ['Incremental compilation of', 'Incremental compilation of'] | ['G_J', 'J.changed']
        'Change Groovy file'                        | ['G', 'J']   | ['G.failure', 'J']   | ['G.changed', 'J']   | ['G']                         | ['UP-TO-DATE', 'Incremental compilation of']                 | ['G.changed', 'J']
    }

    void applyGroovyFileSet(List<String> fileSet) {
        file('src/main/groovy').forceDeleteDir()
        fileSet.each {
            file("src/main/groovy/${it.replace('.changed', '').replace('.failure', '') + (it.startsWith('J') ? '.java' : '.groovy')}").text = SOURCES[it]
        }
    }

    void applyMixFileSet(List<String> fileSet) {
        file('src/main/groovy').forceDeleteDir()
        file('src/main/java').forceDeleteDir()
        fileSet.each {
            if (it.startsWith("J")) {
                file("src/main/java/${it.replace('.changed', '').replace('.failure', '')}.java").text = SOURCES[it]
            } else {
                file("src/main/groovy/${it.replace('.changed', '').replace('.failure', '')}.groovy").text = SOURCES[it]
            }
        }
    }

    void upToDateOrMessage(String message) {
        upToDateOrMessage(":compileGroovy", message)
    }

    void upToDateOrMessage(String taskName, String message) {
        if (message == 'UP-TO-DATE') {
            skipped(taskName)
        } else {
            executedAndNotSkipped(taskName)
        }
        outputContains(message)
    }
}
