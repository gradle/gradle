/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

public class ClasspathTest extends Specification {
    final fileReferenceFactory = new FileReferenceFactory()
    final customEntries = [
        new ProjectDependency("/test2"),
        new Container("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"),
        new Library(fileReferenceFactory.fromPath("/apache-ant-1.7.1/lib/ant-antlr.jar")),
        new SourceFolder("src", "bin2"),
        new Variable(fileReferenceFactory.fromVariablePath("GRADLE_CACHE/ant-1.6.5.jar")),
        new Container("org.eclipse.jdt.USER_LIBRARY/gradle"),
        new Output("bin")]
    final projectDependency = [customEntries[0]]
    final jreContainer = [customEntries[1]]
    final outputLocation = [customEntries[6]]
    final srcFolder = [customEntries[3]]

    final allDependencies = [customEntries[0], customEntries[2], customEntries[4]]

    private final Classpath classpath = new Classpath(new XmlTransformer(), fileReferenceFactory)

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        fileReferenceFactory.addPathVariable("USER_LIB_PATH", new File('/user/lib/path'))
    }

    def "load from reader"() {
        when:
        classpath.load(customClasspathReader)

        then:
        classpath.entries == customEntries
    }

    def "configure overwrites output location, dependencies and jre container and appends all other entries"() {
        when:
        classpath.load(customClasspathReader)
        def newEntries = [createSomeLibrary()] + projectDependency + jreContainer + outputLocation
        classpath.configure(newEntries)

        then:
        def entriesToBeKept = customEntries - allDependencies - jreContainer - outputLocation
        classpath.entries == entriesToBeKept + newEntries
    }

    def "configure overwrites output location, dependencies, source folder and jre container and appends all other entries"() {
        when:
        classpath.load(customClasspathReader)

        def newSource = new SourceFolder("src", "bin4")
        def newEntries = [createSomeLibrary()] + projectDependency + jreContainer + outputLocation + [newSource]
        classpath.configure(newEntries)

        then:
        def entriesToBeKept = customEntries - allDependencies - jreContainer - outputLocation - srcFolder
        classpath.entries == entriesToBeKept + newEntries
    }

    def "load defaults"() {
        when:
        classpath.loadDefaults()

        then:
        classpath.entries == []
    }

    def "toXml contains custom values"() {
        def constructorEntries = [createSomeLibrary()]

        when:
        classpath.load(customClasspathReader)
        classpath.configure(constructorEntries)
        def xml = getToXmlReader()
        def other = new Classpath(new XmlTransformer(), fileReferenceFactory)
        other.load(xml)

        then:
        classpath == other
    }

    def "toXml contains custom values 2"() {
        def constructorEntries = [createSomeLibrary()]

        when:
        classpath.load(customClasspathReader)
        classpath.configure(constructorEntries)
        def xml = getToXmlReader()
        def other = new Classpath(new XmlTransformer(), fileReferenceFactory)
        other.load(xml)

        then:
        classpath == other
    }


    def "create file reference from string"() {
        when:
        FileReference reference = classpath.fileReference(path)

        then:
        reference.path == path
        reference.relativeToPathVariable == isRelative

        where:
        path                 | isRelative
        '/simple/path'       | false
        'USER_LIB_PATH/file' | true
    }

    def 'create file reference from file'() {
        when:
        FileReference reference = classpath.fileReference(new File(path))

        then:
        reference.path.contains(expectedPath) // c: prefix on windows
        reference.relativeToPathVariable == isRelative

        where:
        path                  | expectedPath         | isRelative
        '/simple/path'        | '/simple/path'       | false
        '/user/lib/path/file' | 'USER_LIB_PATH/file' | true
    }

    def 'invalid file reference creation'() {
        when:
        classpath.fileReference(arg)

        then:
        thrown RuntimeException

        where:
        arg << [null, 42]
    }

    private InputStream getCustomClasspathReader() {
        return getClass().getResourceAsStream('customClasspath.xml')
    }

    private Library createSomeLibrary() {
        Library library = new Library(fileReferenceFactory.fromPath("/somepath"))
        library.exported = true
        return library
    }

    private InputStream getToXmlReader() {
        ByteArrayOutputStream toXmlText = new ByteArrayOutputStream()
        classpath.store(toXmlText)
        return new ByteArrayInputStream(toXmlText.toByteArray())
    }
}
