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

abstract class AbstractMultipleLocalJavaModulesCompileIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def "compiles multiple modules in one project using the module path"() {
        given:
        producingModuleInfo('exports producer')
        producingModuleClass()
        consumingModuleInfo('requires producer')
        consumingModuleClass('producer.ProducerClass')

        when:
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
    }

    def "compiles a non-module using the classpath"() {
        given:
        producingModuleInfo()
        producingModuleClass()
        consumingModuleClass('producer.ProducerClass')

        when:
        succeeds ':compileJava' // the internal class can be accessed

        then:
        javaClassFile('consumer/MainModule.class').exists()
    }

    def "fails module compilation for missing requires"() {
        given:
        producingModuleInfo('exports producer')
        producingModuleClass()
        consumingModuleInfo()
        consumingModuleClass('producer.ProducerClass')

        when:
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput '(package producer is declared in module producer, but module consumer does not read it)'
    }

    def "fails module compilation for missing export"() {
        given:
        producingModuleInfo()
        producingModuleClass()
        consumingModuleInfo('requires producer')
        consumingModuleClass('producer.ProducerClass')

        when:
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput '(package producer is declared in module producer, which does not export it)'
    }

    def "compiles a module depending on an automatic module"() {
        given:
        producingModuleClass()
        producingAutomaticModuleNameSetting()
        consumingModuleInfo('requires producer')
        consumingModuleClass('producer.ProducerClass')

        when:
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('consumer/MainModule.class').exists()
    }

    abstract protected producingAutomaticModuleNameSetting();

    abstract protected producingModuleInfo(String... statements)

    abstract protected producingModuleClass()
}
