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
import spock.lang.Unroll

class GroovyJavaJointIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {
    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    static Map sources = [
        'G.groovy': 'class G {}',
        'G.groovy.changed': 'class G { int i }',
        'J.java': 'class J { }',
        'J.java.changed': 'class J { int i; }',

        'J2.java': 'class J2 {}',
        'J2.java.changed': 'class J2 { int i; }',
        'G2.groovy': 'class G2 extends J2{}',

        'J3.java': 'class J3 extends G3 {}',
        'G3.groovy.changed': 'class G3 { int i }',
        'G3.groovy': 'class G3 {}',

        'G4.groovy': 'class G4 {}',
        'G4.groovy.changed': 'class G4 { int i }',
        'G5.groovy': 'class G5 extends G4 {}',
        'J4.java': 'class J4 extends G5 {}',
    ]

    void applyFileSet(List<String> fileSet) {
        file('src/main/groovy').forceDeleteDir()
        fileSet.each {
            try {
                file("src/main/groovy/${it.replace('.changed', '')}").text = sources[it]
            } catch (NullPointerException e) {
                println("NPE: ${it}")
            }
        }
    }

    void upToDateOrMessage(String message) {
        if (message == 'UP-TO-DATE') {
            skipped(":compileGroovy")
        } else {
            executedAndNotSkipped(":compileGroovy")
        }
    }

    @Unroll
    def 'full compilation with Groovy-Java joint compilation'() {
        given:
        applyFileSet(initialSet)

        when:
        run "compileGroovy"
        applyFileSet(firstChange)

        then: 'first build'
        run "compileGroovy", "--info"
        upToDateOrMessage(firstBuildMessage)
        outputContains(firstBuildMessage)

        when: 'second build'
        applyFileSet(secondChange)
        run "compileGroovy", "--info"

        then:
        upToDateOrMessage(secondBuildMessage)
        outputContains(secondBuildMessage)

        where:
        initialSet                            | firstChange                                   | firstBuildMessage                                        | secondChange                                  | secondBuildMessage
        ['G.groovy']                          | ['G.groovy', 'J.java']                        | 'Groovy-Java joint compilation detected'                 | ['G.groovy', 'J.java']                        | 'UP-TO-DATE'
        ['J.java']                            | ['G.groovy', 'J.java']                        | 'no source class mapping file found'                     | ['G.groovy', 'J.java.changed']                | 'Groovy-Java joint compilation detected'
        ['G2.groovy', 'J2.java']              | ['G2.groovy', 'J2.java.changed']              | 'Groovy-Java joint compilation detected'                 | ['G2.groovy', 'J2.java.changed']              | 'UP-TO-DATE'
        ['G3.groovy', 'J3.java']              | ['G3.groovy.changed', 'J3.java']              | 'unable to find source file of class J3'                 | ['G3.groovy.changed', 'J3.java']              | 'UP-TO-DATE'
        ['G.groovy', 'J.java']                | ['G.groovy']                                  | 'Groovy-Java joint compilation detected'                 | ['G.groovy', 'G4.groovy']                     | 'Incremental compilation of '
        ['G.groovy', 'J.java']                | ['J.java']                                    | 'UP-TO-DATE'/*None of the classes needs to be compiled*/ | ['J.java', 'J2.java']                         | 'no source class mapping file found'
        ['G.groovy', 'J.java']                | ['G.groovy', 'J.java', 'G4.groovy']           | 'Incremental compilation of'                             | ['G.groovy', 'J.java', 'G4.groovy']           | 'UP-TO-DATE'
        ['G4.groovy', 'G5.groovy', 'J4.java'] | ['G4.groovy.changed', 'G5.groovy', 'J4.java'] | 'unable to find source file of class J4'                 | ['G4.groovy.changed', 'G5.groovy', 'J4.java'] | 'UP-TO-DATE'
    }
}
