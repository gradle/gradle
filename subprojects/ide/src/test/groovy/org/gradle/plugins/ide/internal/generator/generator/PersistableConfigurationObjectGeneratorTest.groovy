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
package org.gradle.plugins.ide.internal.generator.generator

import spock.lang.Specification

class PersistableConfigurationObjectGeneratorTest extends Specification {
    final PersistableConfigurationObject object = Mock()
    final PersistableConfigurationObjectGenerator<PersistableConfigurationObject> generator = new PersistableConfigurationObjectGenerator<PersistableConfigurationObject>() {
        PersistableConfigurationObject create() {
            return object
        }
        void configure(PersistableConfigurationObject object) {
        }
    }

    def readsObjectFromFile() {
        File inputFile = new File('input')

        when:
        def result = generator.read(inputFile)

        then:
        result == object
        1 * object.load(inputFile)
        0 * _._
    }

    def createsDefaultObject() {
        when:
        def result = generator.defaultInstance()

        then:
        result == object
        1 * object.loadDefaults()
        0 * _._
    }

    def writesObjectToFile() {
        File outputFile = new File('output')

        when:
        generator.write(object, outputFile)

        then:
        1 * object.store(outputFile)
        0 * _._
    }
}
