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

package org.gradle.java.compile.incremental

abstract class AbstractCompileAvoidanceWithIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {
    def setup() {
        buildFile << """
            allprojects {
                ${mavenCentralRepository()}
            }
       """
    }

    def "doesn't recompile if implementation dependency changed in ABI compatible way"() {
        given:
        file('settings.gradle') << "include 'a'\n"
        file("a/src/main/${language.name}/ToolImpl.${language.name}") << '''
            import org.apache.commons.math3.util.BigReal;
            public class ToolImpl { public void execute() { BigReal read = BigReal.ONE; } }
'''
        buildFile << """
            project(':a') {
                apply plugin: '${language.name}'

                dependencies {
                    implementation 'org.apache.commons:commons-math3:3.4'
                }
            }
            """
        configureGroovyIncrementalCompilation("subprojects")

        when:
        succeeds "a:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"

        when:
        buildFile.text = buildFile.text.replace("3.4", "3.4.1")

        then:
        succeeds "a:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
    }
}
