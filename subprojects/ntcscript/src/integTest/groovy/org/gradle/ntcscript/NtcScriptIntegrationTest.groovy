/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.ntcscript

import com.google.common.base.Strings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class NtcScriptIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile """
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
            rootProject.name = 'ntc-test'
        """
    }

    def "can apply plugins and configure extensions"() {
        given:
        ntcScript '''
            [plugins.application]
            [application]
            mainClass = "ntc.App"
        '''
        file('src/main/java/ntc/App.java') << '''
            package ntc;
            public class App {
                public static void main(String[] args) {
                    System.out.println("OMG it's TOML!");
                }
            }
        '''

        when:
        succeeds 'run'

        then:
        outputContains "OMG it's TOML!"
    }

    def "can apply multiple plugins"() {
        given:
        ntcScript '''
            [plugins.application]
            [plugins."org.jetbrains.kotlin.jvm"]
            version = "1.6.10"
            [application]
            mainClass = "ntc.AppKt"
        '''
        file('src/main/kotlin/ntc/App.kt') << '''
            package ntc
            fun main() {
                println("OMG it's TOML!")
            }
        '''

        when:
        succeeds 'run'

        then:
        outputContains "OMG it's TOML!"
    }

    def "can define dependencies"() {
        given:
        ntcScript '''
            [plugins.java-library]
            [dependencies.implementation."com.google.guava"]
            guava = "30.1.1-jre"
        '''
        withLib()

        expect:
        succeeds 'assemble'
    }

    def "can define dependencies using records"() {
        given:
        ntcScript '''
            [plugins.java-library]
            [dependencies.implementation."com.google.guava"]
            guava = { version = "30.1.1-jre" }
        '''
        withLib()

        expect:
        succeeds 'assemble'
    }

    private void withLib() {
        file('src/main/java/ntc/Lib.java') << """
            package ntc;
            public class Lib {
                public boolean isIt(String s) {
                    return ${Strings.name}.isNullOrEmpty(s);
                }
            }
        """
    }

    protected TestFile ntcScript(@NtcBuildScriptLanguage String script) {
        file('build.gradle.toml').tap {
            it.text = script
        }
    }
}
