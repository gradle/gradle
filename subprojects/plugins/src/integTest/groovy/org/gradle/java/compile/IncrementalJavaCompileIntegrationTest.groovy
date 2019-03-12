/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class IncrementalJavaCompileIntegrationTest extends AbstractIntegrationSpec implements IncrementalCompileMultiProjectTestFixture {

    def "recompiles source when properties change"() {
        given:
        file('src/main/java/Test.java') << 'public class Test{}'
        buildFile << '''
            apply plugin: 'java'
            sourceCompatibility = 1.7
            compileJava.options.debug = true
        '''.stripIndent()

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':compileJava'

        when:
        buildFile << 'sourceCompatibility = 1.8\n'
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':compileJava'

        when:
        buildFile << 'compileJava.options.debug = false\n'
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':compileJava'

        when:
        succeeds ':compileJava'

        then:
        skipped ':compileJava'
    }

    def "recompiles dependent classes"() {
        given:
        file('src/main/java/IPerson.java') << basicInterface
        file('src/main/java/Person.java') << classImplementingBasicInterface
        buildFile << 'apply plugin: "java"\n'

        expect:
        succeeds 'classes'

        when: 'update interface, compile should fail'
        file('src/main/java/IPerson.java').text = extendedInterface

        then:
        def failure = fails 'classes'
        failure.assertHasDescription "Execution failed for task ':compileJava'."
    }

    def "recompiles dependent classes across project boundaries"() {
        given:
        file('lib/src/main/java/IPerson.java') << basicInterface
        file('app/src/main/java/Person.java') << classImplementingBasicInterface
        settingsFile << 'include "lib", "app"'
        buildFile << '''
            subprojects {
                apply plugin: 'java'
            }            
            project(':app') {
                dependencies {
                    compile project(':lib')
                }
            }
        '''.stripIndent()

        expect:
        succeeds 'app:classes'

        when: 'update interface, compile should fail'
        file('lib/src/main/java/IPerson.java').text = extendedInterface

        then:
        def failure = fails 'app:classes'
        failure.assertHasDescription "Execution failed for task ':app:compileJava'."
    }

    def "task outcome is UP-TO-DATE when no recompilation necessary"() {
        given:
        libraryAppProjectWithIncrementalCompilation()

        when:
        succeeds appCompileJava

        then:
        executedAndNotSkipped appCompileJava

        when:
        writeUnusedLibraryClass()

        then:
        executer.withArgument('-i')
        succeeds appCompileJava

        and:
        outputContains "None of the classes needs to be compiled!"
        outputContains "${appCompileJava} UP-TO-DATE"
        executedAndNotSkipped(libraryCompileJava)
    }

    @Unroll
    def "does not recompile when only compileOptions.incremental property changes from #from to #to"() {
        given:
        libraryAppProjectWithIncrementalCompilation()

        when:
        buildFile << """
            subprojects {
                tasks.compileJava.options.incremental = $from
            }
        """
        run appCompileJava

        then:
        executedAndNotSkipped libraryCompileJava
        executedAndNotSkipped appCompileJava

        when:
        buildFile << """
            subprojects {
                tasks.compileJava.options.incremental = $to
            }
        """
        run appCompileJava

        then:
        skipped libraryCompileJava
        skipped appCompileJava

        where:
        from  | to
        true  | false
        false | true
    }

    private static String getBasicInterface() {
        '''
            interface IPerson {
                String getName();
            }
        '''.stripIndent()
    }

    private static String getExtendedInterface() {
        '''
            interface IPerson {
                String getName();
                String getAddress();
            }
        '''.stripIndent()
    }

    private static String getClassImplementingBasicInterface() {
        '''
            class Person implements IPerson {
                public String getName() {
                    return "name";
                }
            }
        '''.stripIndent()
    }
}
