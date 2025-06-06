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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import spock.lang.Issue

class GroovyClassChangeIncrementalCompilationIntegrationTest extends AbstractClassChangeIncrementalCompilationIntegrationTest implements DirectoryBuildCacheFixture {
    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        succeeds language.compileTaskName
        outputs.recompiledClasses(recompiledClasses)
    }

    @Issue("https://github.com/gradle/gradle/issues/33161")
    def "detects changes to class referenced in method body via lambda"() {
        given:
        source '''class A {
            void doSomething() {
                takeRunnable((B) () -> {
                    System.out.println("Hello");
                });
            }

            void takeRunnable(Runnable r) {
                r.run();
            }
        }'''
        source "interface B extends Runnable {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('takeRunnable((B) () -> {', 'A', 'B', 'A$_doSomething_closure1')
    }
}
