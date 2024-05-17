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

package org.gradle.internal.classloader

import spock.lang.Specification

class VisitableURLClassLoaderTest extends Specification {
    def "visits self and parent"() {
        def visitor = Mock(ClassLoaderVisitor)
        def parent = new ClassLoader(null) { }
        def classPath = [new File("a").toURI().toURL(), new File("b").toURI().toURL()]
        def cl = new VisitableURLClassLoader("test", parent, classPath)

        when:
        cl.visit(visitor)

        then:
        1 * visitor.visitSpec({it instanceof VisitableURLClassLoader.Spec}) >> { VisitableURLClassLoader.Spec spec ->
            assert spec.name == "test"
            assert spec.classpath == classPath
        }
        1 * visitor.visitClassPath(classPath)
        1 * visitor.visitParent(parent)
        0 * visitor._
    }
}
