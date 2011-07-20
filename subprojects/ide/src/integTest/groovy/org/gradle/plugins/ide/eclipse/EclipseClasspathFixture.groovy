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
import java.util.regex.Pattern

class EclipseClasspathFixture {
    final TestFile userHomeDir
    final TestFile projectDir
    final String userHomeDirVar
    Node classpath

    EclipseClasspathFixture(TestFile userHomeDir, TestFile projectDir) {
        this(null, userHomeDir, projectDir)
    }

    private EclipseClasspathFixture(String userHomeDirVar, TestFile userHomeDir, TestFile projectDir) {
        this.userHomeDirVar = userHomeDirVar
        this.userHomeDir = userHomeDir
        this.projectDir = projectDir
    }

    EclipseClasspathFixture withHomeDir(String homeDirVar) {
        return new EclipseClasspathFixture(homeDirVar, userHomeDir, projectDir)
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

        void assertHasCachedJar(String group, String module, String version) {
            assert entry.@path ==~ cachedArtifact(group, module, version)
        }

        void assertHasCachedSource(String group, String module, String version) {
            assert entry.@sourcepath ==~ cachedArtifact(group, module, version, "sources")
        }

        void assertHasNoSource() {
            assert !entry.@sourcepath
        }

        void assertHasCachedJavadoc(String group, String module, String version) {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'javadoc_location'
            assert entry.attributes[0].attribute[0].@value ==~ cachedArtifact(group, module, version, "javadoc")
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

        final Pattern cachedArtifact(group, module, version, classifier = null) {
            def type = classifier == 'javadoc' ? 'javadocs' : classifier ?: "jars"
            def cacheLocation = "cache/${group}/${module}/${type}/${module}-${version}${classifier ? '-' + classifier : ''}.jar"
            def dir
            if (userHomeDirVar) {
                dir = "${userHomeDirVar}/${cacheLocation}"
            } else {
                dir = userHomeDir.file(cacheLocation).absolutePath.replace(File.separator, '/')
            }
            return Pattern.compile(Pattern.quote(dir))
        }
    }
}

