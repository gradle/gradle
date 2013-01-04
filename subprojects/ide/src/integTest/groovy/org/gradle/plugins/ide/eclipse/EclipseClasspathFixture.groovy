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

import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager
import org.gradle.test.fixtures.file.TestFile

import java.util.regex.Pattern

class EclipseClasspathFixture {
    final TestFile projectDir
    final TestFile userHomeDir
    private Node classpath

    EclipseClasspathFixture(TestFile projectDir, TestFile userHomeDir) {
        this.projectDir = projectDir
        this.userHomeDir = userHomeDir
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

    String getOutput() {
        return getClasspath().classpathentry.find { it.@kind == 'output' }.@path
    }

    List<String> getContainers() {
        return getClasspath().classpathentry.findAll { it.@kind == 'con' }.collect { it.@path }
    }

    List<String> getSources() {
        return getClasspath().classpathentry.findAll{ it.@kind == 'src' && !it.@path.startsWith('/') }.collect { it.@path }
    }

    List<String> getProjects() {
        return getClasspath().classpathentry.findAll { it.@kind == 'src' && it.@path.startsWith('/') }.collect { it.@path }
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
            assert entry.@path ==~ cachePath(group, module, version, "jar") + Pattern.quote("${module}-${version}.jar")
        }

        void assertHasSource(File jar) {
            assert entry.@sourcepath == jar.absolutePath.replace(File.separator, '/')
        }

        void assertHasSource(String jar) {
            assert entry.@sourcepath == jar
        }

        void assertHasCachedSource(String group, String module, String version) {
            assert entry.@sourcepath ==~ cachePath(group, module, version, "source") + Pattern.quote("${module}-${version}-sources.jar")
        }

        private String cachePath(String group, String module, String version, String type) {
            return Pattern.quote("${userHomeDir.absolutePath.replace(File.separator, '/')}") + "/caches/artifacts-${artifactCacheVersion}/filestore/" + Pattern.quote("${group}/${module}/${version}/${type}/") + "\\w+/"
        }

        private def getArtifactCacheVersion() {
            return DefaultCacheLockingManager.CACHE_LAYOUT_VERSION;
        }

        void assertHasNoSource() {
            assert !entry.@sourcepath
        }

        void assertHasJavadoc(File file) {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'javadoc_location'
            assert entry.attributes[0].attribute[0].@value == jarUrl(file)
        }

        private String jarUrl(File filePath){
            "jar:${filePath.toURI().toURL()}!/"
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

