/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal

import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class GeneratorTaskTest extends AbstractProjectBuilderSpec {
    final Generator<TestConfigurationObject> generator = Mock()
    final File inputFile = temporaryFolder.file('input')
    final File outputFile = temporaryFolder.file('output')
    final GeneratorTask<TestConfigurationObject> task = TestUtil.create(temporaryFolder).task(GeneratorTask)

    def setup() {
        task.inputFile = inputFile
        task.outputFile = outputFile
        task.generator = generator
    }

    def usesOutputFileAsDefaultInputFile() {
        when:
        task.inputFile = null

        then:
        task.inputFile == task.outputFile

        when:
        task.inputFile = inputFile

        then:
        task.inputFile == inputFile
    }

    def mergesConfigurationWhenInputFileExists() {
        def configObject = new TestConfigurationObject()
        inputFile.text = 'config'

        when:
        task.generate()

        then:
        1 * generator.read(inputFile) >> configObject
        1 * generator.configure(configObject)
        1 * generator.write(configObject, outputFile)
        0 * _._
    }

    def generatesConfigurationWhenInputFileDoesNotExist() {
        def configObject = new TestConfigurationObject()

        when:
        task.generate()

        then:
        1 * generator.defaultInstance() >> configObject
        1 * generator.configure(configObject)
        1 * generator.write(configObject, outputFile)
        0 * _._
    }

}

class TestConfigurationObject {

}
