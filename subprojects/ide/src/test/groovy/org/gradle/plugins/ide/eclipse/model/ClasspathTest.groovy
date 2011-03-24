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
package org.gradle.plugins.ide.eclipse.model;


import org.gradle.api.internal.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
public class ClasspathTest extends Specification {
    private static final CUSTOM_ENTRIES = [
            new ProjectDependency("/test2", false, null, [] as Set, null),
            new Container("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6",
                false, null, [] as Set),
            new Library("/apache-ant-1.7.1/lib/ant-antlr.jar", false, null, [] as Set, null, null),
            new SourceFolder("src", null, [] as Set, "bin2", [], []),
            new Variable("GRADLE_CACHE/ant-1.6.5.jar", false, null, [] as Set, null, null),
            new Container("org.eclipse.jdt.USER_LIBRARY/gradle", false, null, [] as Set),
            new Output("bin")]
    private static final PROJECT_DEPENDENCY = [CUSTOM_ENTRIES[0]]
    private static final ALL_DEPENDENCIES = [CUSTOM_ENTRIES[0], CUSTOM_ENTRIES[2]]

    private final Classpath classpath = new Classpath(new XmlTransformer())

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder()

    def "load from reader"() {
        when:
        classpath.load(customClasspathReader)

        then:
        classpath.entries == CUSTOM_ENTRIES
    }

    def "configure overwrites dependencies and appends all other entries"() {
        def constructorEntries = [createSomeLibrary()]

        when:
        classpath.load(customClasspathReader)
        def newEntries = constructorEntries + PROJECT_DEPENDENCY
        classpath.configure(newEntries)

        then:
        def entriesToBeKept = CUSTOM_ENTRIES - ALL_DEPENDENCIES
        classpath.entries ==  entriesToBeKept + newEntries
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