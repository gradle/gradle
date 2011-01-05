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


import org.gradle.api.internal.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */

public class ClasspathTest extends Specification {
    private static final CUSTOM_ENTRIES = [
            new ProjectDependency("/test2", false, null, [] as Set),
            new Container("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6",
                false, null, [] as Set),
            new Library("/apache-ant-1.7.1/lib/ant-antlr.jar", false, null, [] as Set, null, null),
            new SourceFolder("src", null, [] as Set, "bin2", [], []),
            new Variable("GRADLE_CACHE/ant-1.6.5.jar", false, null, [] as Set, null, null),
            new Container("org.eclipse.jdt.USER_LIBRARY/gradle", false, null, [] as Set),
            new Output("bin")]
    final Classpath classpath = new Classpath(new XmlTransformer())

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def loadFromReader() {
        when:
        classpath.load(customClasspathReader)

        then:
        classpath.entries == CUSTOM_ENTRIES
    }

    def configureMergesEntries() {
        def constructorEntries = [createSomeLibrary()]

        when:
        classpath.load(customClasspathReader)
        classpath.configure(constructorEntries + [CUSTOM_ENTRIES[0]])

        then:
        classpath.entries == CUSTOM_ENTRIES + constructorEntries
    }

    def loadDefaults() {
        when:
        classpath.loadDefaults()

        then:
        classpath.entries == []
    }

    def toXml_shouldContainCustomValues() {
        def constructorEntries = [createSomeLibrary()]

        when:
        classpath.load(customClasspathReader)
        classpath.configure(constructorEntries)
        def xml = getToXmlReader()
        def other = new Classpath(new XmlTransformer())
        other.load(xml)

        then:
        classpath == other
    }

    private InputStream getCustomClasspathReader() {
        return getClass().getResourceAsStream('customClasspath.xml')
    }

    private Library createSomeLibrary() {
        return new Library("/somepath", true, null, [] as Set, null, null)
    }

    private InputStream getToXmlReader() {
        ByteArrayOutputStream toXmlText = new ByteArrayOutputStream()
        classpath.store(toXmlText)
        return new ByteArrayInputStream(toXmlText.toByteArray())
    }
}