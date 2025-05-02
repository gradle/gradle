/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms.compile

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class JavaModuleCrossCompileIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile << """
            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            def jvm = project.services.get(org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities)

            def main = sourceSets.main
            jvm.registerJvmLanguageSourceDirectory(main, "java9") {
                it.withDescription("Java 9 Sources")
                it.compiledWithJava {
                    sourceCompatibility = '9'
                    targetCompatibility = '9'
                    classpath = main.compileClasspath
                }
            }
        """
    }

    def "compiles a module that can be used as library with Java 8"() {
        given:
        file('src/main/java9/module-info.java').text = "module consumer { }"
        consumingModuleClass()

        when:
        succeeds ':classes'

        then:
        executed(':compileJava', ':compileJava9')
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java9', 'main', 'module-info.class').exists()
    }

}
