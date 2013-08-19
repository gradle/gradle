/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal

import org.gradle.model.ModelType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

class PersistentModelObjectRegistryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "persists the public properties of a Groovy model object"() {
        def model = new GroovyTestModel(prop1: "value", prop2: 12, prop3: ["a", "b", "c"])
        def storeFile = tmpDir.file("model.bin")

        when:
        def writeRegistry = new PersistentModelObjectRegistry(storeFile)
        writeRegistry.put("a", model)
        writeRegistry.close()

        def readRegistry = new PersistentModelObjectRegistry(storeFile)
        def result = readRegistry.get("a", GroovyTestModel)
        readRegistry.close()

        then:
        result instanceof GroovyTestModel
        result != model
        result.prop1 == model.prop1
        result.prop2 == model.prop2
        result.prop3 == model.prop3
    }

    def "persists the public properties of a Java model object"() {
        def model = new TestModel(prop1: "value", prop2: 12, prop3: ["a", "b", "c"])
        def storeFile = tmpDir.file("model.bin")

        when:
        def writeRegistry = new PersistentModelObjectRegistry(storeFile)
        writeRegistry.put("a", model)
        writeRegistry.close()

        def readRegistry = new PersistentModelObjectRegistry(storeFile)
        def result = readRegistry.get("a", TestModel)
        readRegistry.close()

        then:
        result instanceof TestModel
        result != model
        result.prop1 == model.prop1
        result.prop2 == model.prop2
        result.prop3 == model.prop3
    }

    def "cannot put an object which is not a model object"() {
        def storeFile = tmpDir.file("model.bin")
        def registry = new PersistentModelObjectRegistry(storeFile)

        when:
        registry.put("a", 12)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot persist object of class Integer, as this class is not marked @ModelType'

        cleanup:
        registry.close()
    }

    def "model object can reference another model object"() {
        def storeFile = tmpDir.file("model.bin")
        def child = new TestModel(prop1: "child")
        def parent = new ParentModel(child: child, child2: child)

        when:
        def writeRegistry = new PersistentModelObjectRegistry(storeFile)
        writeRegistry.put("child", child)
        writeRegistry.put("parent", parent)
        writeRegistry.close()

        def readRegistry = new PersistentModelObjectRegistry(storeFile)
        def result = readRegistry.get("parent", ParentModel)
        readRegistry.close()

        then:
        result.child instanceof TestModel
        result.child.prop1 == "child"
        result.child.is(result.child2)
    }

    def "model object instance is reused"() {
        def storeFile = tmpDir.file("model.bin")
        def child = new TestModel(prop1: "child")
        def parent1 = new ParentModel(child: child)
        def parent2 = new ParentModel(child: child)

        when:
        def writeRegistry = new PersistentModelObjectRegistry(storeFile)
        writeRegistry.put("child", child)
        writeRegistry.put("parent1", parent1)
        writeRegistry.put("parent2", parent2)
        writeRegistry.close()

        def readRegistry = new PersistentModelObjectRegistry(storeFile)
        def result1 = readRegistry.get("parent1", ParentModel)
        def result2 = readRegistry.get("parent2", ParentModel)
        readRegistry.close()

        then:
        result1.child.is(result2.child)
    }

    def "returns null when object with given identifier is not present"() {
        def storeFile = tmpDir.file("model.bin")
        def registry = new PersistentModelObjectRegistry(storeFile)

        expect:
        registry.get("some-id", TestModel) == null

        cleanup:
        registry.close()
    }

    @Ignore
    def "rethrows failure to get the value of a property"() {
        expect: false
    }

    @Ignore
    def "rethrows failure to construct an instance"() {
        expect: false
    }

    @Ignore
    def "rethrows failure to set the value of a property"() {
        expect: false
    }

    @ModelType
    static class GroovyTestModel {
        String prop1
        int prop2
        List<String> prop3
    }

    @ModelType
    static class ParentModel {
        Object child
        Object child2
    }
}
