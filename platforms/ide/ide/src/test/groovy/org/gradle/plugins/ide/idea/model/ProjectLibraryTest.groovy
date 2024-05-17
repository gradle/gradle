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

package org.gradle.plugins.ide.idea.model

import groovy.xml.XmlNodePrinter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectLibraryTest extends Specification {
    @Rule TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider(getClass())

    def "has friendly defaults"() {
        def library = new ProjectLibrary()

        expect:
        with(library) {
            name == null
            classes == [] as Set
            sources == [] as Set
            javadoc == [] as Set
        }
    }

    def "defines equality as deep equality of all its properties"() {
        expect:
        new ProjectLibrary() == new ProjectLibrary()
        new ProjectLibrary(name: "lib1") == new ProjectLibrary(name: "lib1")
        new ProjectLibrary(name: "lib1", classes:  [new File("class/one"), new File("class/two")]) ==
                new ProjectLibrary(name: "lib1", classes: [new File("class/two"), new File("class/one")])

        new ProjectLibrary(name: "lib1") != new ProjectLibrary(name: "OTHER")
        new ProjectLibrary(name: "lib1", classes:  [new File("class/one"), new File("class/two")]) !=
                new ProjectLibrary(name: "lib1", classes:  [new File("class/one"), new File("class/OTHER")])
    }

    def "generates correct XML"() {
        def userHome = testDirProvider.testDirectory

        def lib = new ProjectLibrary(name: "lib",
                classes: [new File(userHome, "class/one.jar"), new File(userHome, "class/two.jar")] as LinkedHashSet,
                javadoc: [new File(userHome, "javadoc/one.jar"), new File(userHome, "javadoc/two.jar")] as LinkedHashSet,
                sources: [new File(userHome, "source/one.jar"), new File(userHome, "source/two.jar")] as LinkedHashSet)

        def pathFactory = new PathFactory()
        pathFactory.addPathVariable("USER_HOME", userHome)

        when:
        def parent = new Node(null, "parent")
        lib.addToNode(parent, pathFactory)

        then:
        def writer = new StringWriter()
        def printer = new XmlNodePrinter(new IndentPrinter(writer))
        printer.print(parent)
        writer.toString().trim() == """
<parent>
  <library name="lib">
    <CLASSES>
      <root url="jar://\$USER_HOME\$/class/one.jar!/"/>
      <root url="jar://\$USER_HOME\$/class/two.jar!/"/>
    </CLASSES>
    <JAVADOC>
      <root url="jar://\$USER_HOME\$/javadoc/one.jar!/"/>
      <root url="jar://\$USER_HOME\$/javadoc/two.jar!/"/>
    </JAVADOC>
    <SOURCES>
      <root url="jar://\$USER_HOME\$/source/one.jar!/"/>
      <root url="jar://\$USER_HOME\$/source/two.jar!/"/>
    </SOURCES>
  </library>
</parent>
""".trim()
    }
}
