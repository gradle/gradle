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
package org.gradle.plugins.eclipse.model;


import org.gradle.api.Action
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.internal.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */

public class ClasspathTest extends Specification {
    private static final DEFAULT_ENTRIES = [new Output('bin')]
    private static final CUSTOM_ENTRIES = [
            new ProjectDependency("/test2", false, null, [] as Set),
            new Container("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6",
                false, null, [] as Set),
            new Library("/apache-ant-1.7.1/lib/ant-antlr.jar", false, null, [] as Set, null, null),
            new SourceFolder("src", null, [] as Set, "bin2", [], []),
            new Variable("GRADLE_CACHE/ant-1.6.5.jar", false, null, [] as Set, null, null),
            new Container("org.eclipse.jdt.USER_LIBRARY/gradle", false, null, [] as Set)]

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def initWithReader() {
        Classpath classpath = createClasspath(reader: customClasspathReader)

        expect:
        classpath.entries == DEFAULT_ENTRIES + CUSTOM_ENTRIES

    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorDefaultOutput = 'build'
        def constructorEntries = [createSomeLibrary()]

        Classpath classpath = createClasspath(entries: constructorEntries + [CUSTOM_ENTRIES[0]], defaultOutput: constructorDefaultOutput,
                reader: customClasspathReader)

        expect:
        classpath.entries == DEFAULT_ENTRIES + CUSTOM_ENTRIES + constructorEntries
    }

    def initWithNullReader() {
        def constructorDefaultOutput = 'build'
        def constructorEntries = [createSomeLibrary()]

        Classpath classpath = createClasspath(entries: constructorEntries, defaultOutput: constructorDefaultOutput)

        expect:
        classpath.xml != null
        classpath.entries == DEFAULT_ENTRIES + constructorEntries
    }

    def toXml() {
        when:
        Classpath classpath = createClasspath(reader: customClasspathReader)

        then:
        File eclipseFile = tmpDir.file("eclipse.xml")
        classpath.toXml(eclipseFile)
        StringWriter stringWriterFileXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterFileXml)).print(new XmlParser().parse(eclipseFile))
        StringWriter stringWriterWrittenXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterWrittenXml)).print(new XmlParser().parse(getToXmlReader(classpath)))
        StringWriter stringWriterInternalXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterInternalXml)).print(classpath.xml)

        stringWriterWrittenXml.toString() == stringWriterInternalXml.toString()
        stringWriterWrittenXml.toString() == stringWriterFileXml.toString()
    }

    def toXml_shouldContainCustomValues() {
        def constructorEntries = [createSomeLibrary()]

        when:
        Classpath classpath = createClasspath(entries: constructorEntries,
                reader: customClasspathReader)
        def classpathFromXml = createClasspath(reader: getToXmlReader(classpath))

        then:
        classpath == classpathFromXml
    }

    def beforeConfigured() {
        def constructorEntries = [createSomeLibrary()]
        Action beforeConfiguredAction = { Classpath classpath ->
            classpath.entries.clear()
        } as Action

        when:
        Classpath classpath = createClasspath(entries: constructorEntries, reader: customClasspathReader,
                beforeConfiguredActions: beforeConfiguredAction)

        then:
        createClasspath(reader: getToXmlReader(classpath)).entries == DEFAULT_ENTRIES + constructorEntries
    }

    def whenConfigured() {
        def constructorEntry = createSomeLibrary()
        def configureActionEntry = createSomeLibrary()
        configureActionEntry.path = constructorEntry.path + 'Other'

        Action whenConfiguredActions = { Classpath classpath ->
            assert classpath.entries.contains((CUSTOM_ENTRIES as List)[0])
            assert classpath.entries.contains(constructorEntry)
            classpath.entries.add(configureActionEntry)
        } as Action

        when:
        Classpath classpath = createClasspath(entries: [constructorEntry], reader: customClasspathReader,
                whenConfiguredActions: whenConfiguredActions)

        then:
        createClasspath(reader: getToXmlReader(classpath)).entries == DEFAULT_ENTRIES + CUSTOM_ENTRIES +
                ([constructorEntry, configureActionEntry])
    }

    def withXml() {
        XmlTransformer withXmlActions = new XmlTransformer()
        Classpath classpath = createClasspath(reader: customClasspathReader, withXmlActions: withXmlActions)

        when:
        withXmlActions.addAction { XmlProvider xml ->
            xml.asNode().classpathentry.find { it.@kind == 'output' }.@path = 'newPath'
        }

        then:
        new XmlParser().parse(getToXmlReader(classpath)).classpathentry.find { it.@kind == 'output' }.@path == 'newPath'
    }

    private InputStreamReader getCustomClasspathReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customClasspath.xml'))
    }

    private Library createSomeLibrary() {
        return new Library("/somepath", true, null, [] as Set, null, null)
    }

    private Classpath createClasspath(Map customArgs) {
        Action dummyBroadcast = Mock()
        XmlTransformer transformer = new XmlTransformer()
        Map args = [entries: [], reader: null, beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: transformer] + customArgs
        return new Classpath(args.beforeConfiguredActions, args.whenConfiguredActions, args.withXmlActions, args.entries, args.reader)
    }

    private StringReader getToXmlReader(Classpath classpath) {
        StringWriter toXmlText = new StringWriter()
        classpath.toXml(toXmlText)
        return new StringReader(toXmlText.toString())
    }
}