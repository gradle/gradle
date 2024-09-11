/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.serialize

import spock.lang.Specification

import java.lang.reflect.Array

class ClassLoaderObjectInputStreamTest extends Specification {

    def "should handle array types"() {
        setup:
        GroovyClassLoader gcl = new GroovyClassLoader()
        def myClass = gcl.parseClass("class MyClass {}")
        def arrayObject = Array.newInstance(myClass, 10)
        def output = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(output)
        oos.writeObject(arrayObject)
        oos.close()

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        def ois = new ClassLoaderObjectInputStream(input, gcl)
        def readArrayObject = ois.readObject()

        then:
        readArrayObject.class.isArray()
        readArrayObject.class.componentType.name == "MyClass"
    }
}
