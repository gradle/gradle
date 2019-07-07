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
        'G2.groovy': 'class G2 {}'
    ]

    void applyFileSet(List<String> fileSet) {
        file('src/main/groovy').forceDeleteDir()
        fileSet.each {
            file("src/main/groovy/${it.replace('.changed', '')}").text = sources[it]
        }
    }

    @Unroll
    def 'full compilation with Groovy-Java joint compilation'() {
        given:
        applyFileSet(initialSet)

        when:
        run "compileGroovy"
        applyFileSet(firstChange)

        then:
        run "compileGroovy", "--info"
        executedAndNotSkipped(":compileGroovy")
        outputContains(firstRecompilationReason)

        when:
        applyFileSet(secondChange)
        run "compileGroovy", "--info"

        then:
        if (secondRecompilationReason == 'UP-TO-DATE') {
            skipped(":compileGroovy")
        } else {
            executedAndNotSkipped(":compileGroovy")
        }
        outputContains(secondRecompilationReason)

        where:
        initialSet             | firstChange                         | firstRecompilationReason                 | secondChange                        | secondRecompilationReason
        ['G.groovy']           | ['G.groovy', 'J.java']              | 'Groovy-Java joint compilation detected' | ['G.groovy', 'J.java']              | 'UP-TO-DATE'
        ['J.java']             | ['G.groovy', 'J.java']              | 'no source class mapping file found'     | ['G.groovy', 'J.java.changed']      | 'no source class mapping file found'
        ['G.groovy', 'J.java'] | ['G.groovy', 'J.java.changed']      | 'no source class mapping file found'     | ['G.groovy', 'J.java.changed']      | 'UP-TO-DATE'
        ['G.groovy', 'J.java'] | ['G.groovy.changed', 'J.java']      | 'no source class mapping file found'     | ['G.groovy.changed', 'J.java']      | 'UP-TO-DATE'
        ['G.groovy', 'J.java'] | ['G.groovy']                        | 'no source class mapping file found'     | ['G.groovy', 'G2.groovy']           | 'Incremental compilation of '
        ['G.groovy', 'J.java'] | ['J.java']                          | 'no source class mapping file found'     | ['J.java', 'J2.java']               | 'no source class mapping file found'
        ['G.groovy', 'J.java'] | ['G.groovy', 'J.java', 'J2.java']   | 'no source class mapping file found'     | ['G.groovy', 'G2.groovy']           | 'no source class mapping file found'
        ['G.groovy', 'J.java'] | ['G.groovy', 'J.java', 'G2.groovy'] | 'no source class mapping file found'     | ['G.groovy', 'J.java', 'G2.groovy'] | 'UP-TO-DATE'
    }
}
