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

import spock.lang.Specification

class ProjectLibraryTest extends Specification {
    def "has friendly defaults"() {
        def library = new ProjectLibrary()

        expect:
        with(library) {
            name == null
            classes == [] as Set
            sources == [] as Set
            javadoc == [] as Set
            jarDirectories == [] as Set
        }
    }

    def "defines equality as deep equality of all its properties"() {
        expect:
        new ProjectLibrary() == new ProjectLibrary()
        new ProjectLibrary(name: "lib1") == new ProjectLibrary(name: "lib1")
        new ProjectLibrary(name: "lib1", classes:  [new Path("class/one"), new Path("class/two")]) ==
                new ProjectLibrary(name: "lib1", classes: [new Path("class/two"), new Path("class/one")])

        new ProjectLibrary(name: "lib1") != new ProjectLibrary(name: "OTHER")
        new ProjectLibrary(name: "lib1", classes:  [new Path("class/one"), new Path("class/two")]) !=
                new ProjectLibrary(name: "lib1", classes:  [new Path("class/one"), new Path("class/OTHER")])
    }

    def "generates correct XML"() {
        def lib = new ProjectLibrary(name: "lib",
                classes: [new Path("class/one"), new Path("class/two")] as LinkedHashSet,
                javadoc: [new Path("javadoc/one"), new Path("javadoc/two")] as LinkedHashSet,
                sources: [new Path("source/one"), new Path("source/two")] as LinkedHashSet,
                jarDirectories: [new JarDirectory(new Path("jardir/one"), true),
                        new JarDirectory(new Path("jardir/two"), false)] as LinkedHashSet)

        when:
        def parent = new Node(null, "parent")
        lib.addToNode(parent)

        then:
        def writer = new StringWriter()
        def printer = new XmlNodePrinter(new IndentPrinter(writer))
        printer.print(parent)
        writer.toString().trim() == """
<parent>
  <library name="lib">
    <CLASSES>
      <root url="class/one"/>
      <root url="class/two"/>
    </CLASSES>
    <JAVADOC>
      <root url="javadoc/one"/>
      <root url="javadoc/two"/>
    </JAVADOC>
    <SOURCES>
      <root url="source/one"/>
      <root url="source/two"/>
    </SOURCES>
    <jarDirectory url="jardir/one" recursive="true"/>
    <jarDirectory url="jardir/two" recursive="false"/>
  </library>
</parent>
""".trim()
    }
}
