/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.util.TestFile

class EclipseClasspathFixture {
    final TestFile projectDir
    Node classpath

    EclipseClasspathFixture(TestFile projectDir) {
        this.projectDir = projectDir
    }

    Node getClasspath() {
        if (classpath == null) {
            TestFile file = projectDir.file('.classpath')
            println "Using .classpath:"
            println file.text
            classpath = new XmlParser().parse(file)
        }
        return classpath
    }

    List<Node> getEntries() {
        return getClasspath().classpathentry as List
    }

    List<EclipseLibrary> getLibs() {
        return getClasspath().classpathentry.findAll { it.@kind == 'lib' }.collect { new EclipseLibrary(it) }
    }

    List<EclipseLibrary> getVars() {
        return getClasspath().classpathentry.findAll { it.@kind == 'var' }.collect { new EclipseLibrary(it) }
    }

    class EclipseLibrary {
        final Node entry

        EclipseLibrary(Node entry) {
            this.entry = entry
        }

        void assertHasJar(File jar) {
            assert entry.@path == jar.absolutePath.replace(File.separator, '/')
        }

        void assertHasJar(String jar) {
            assert entry.@path == jar
        }

        void assertHasSource(File jar) {
            assert entry.@sourcepath == jar.absolutePath.replace(File.separator, '/')
        }

        void assertHasSource(String jar) {
            assert entry.@sourcepath == jar
        }

        void assertHasNoSource() {
            assert !entry.@sourcepath
        }

        void assertHasJavadoc(File file) {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'javadoc_location'
            assert entry.attributes[0].attribute[0].@value == file.absolutePath.replace(File.separator, '/')
        }

        void assertHasJavadoc(String file) {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'javadoc_location'
            assert entry.attributes[0].attribute[0].@value == file
        }

        void assertHasNoJavadoc() {
            assert entry.attributes.size() == 0
        }

        void assertExported() {
            assert entry.@exported == 'true'
        }

        void assertNotExported() {
            assert !entry.@exported
        }
    }
}

