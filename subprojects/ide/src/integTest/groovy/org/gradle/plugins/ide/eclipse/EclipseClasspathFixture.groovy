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

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.test.fixtures.file.TestFile

import java.util.regex.Pattern

class EclipseClasspathFixture {
    final TestFile userHomeDir
    final Node classpath

    private EclipseClasspathFixture(TestFile userHomeDir, Node classpath) {
        this.userHomeDir = userHomeDir
        this.classpath = classpath
    }

    static EclipseClasspathFixture create(TestFile projectDir, TestFile userHomeDir) {
        TestFile file = projectDir.file('.classpath')
        file.assertExists()
        return new EclipseClasspathFixture(userHomeDir, new XmlParser().parse(file))
    }

    List<Node> getEntries() {
        return this.classpath.classpathentry as List
    }

    String getOutput() {
        return this.classpath.classpathentry.find { it.@kind == 'output' }.@path
    }

    List<String> getContainers() {
        return this.classpath.classpathentry.findAll { it.@kind == 'con' }.collect { it.@path }
    }

    List<String> getSources() {
        return this.classpath.classpathentry.findAll{ it.@kind == 'src' && !it.@path.startsWith('/') }.collect { it.@path }
    }

    List<String> getProjects() {
        return this.classpath.classpathentry.findAll { it.@kind == 'src' && it.@path.startsWith('/') }.collect { it.@path }
    }

    void assertHasLibs(String... jarNames) {
        assert libs*.jarName == jarNames as List
    }

    EclipseLibrary lib(String jarName) {
        def matches = libs.findAll { it.jarName == jarName } + vars.findAll { it.jarName == jarName }
        assert matches.size() == 1
        return matches[0]
    }

    List<EclipseLibrary> getLibs() {
        return this.classpath.classpathentry.findAll { it.@kind == 'lib' }.collect { new EclipseLibrary(it) }
    }

    List<EclipseLibrary> getVars() {
        return this.classpath.classpathentry.findAll { it.@kind == 'var' }.collect { new EclipseLibrary(it) }
    }

    class EclipseLibrary {
        final Node entry

        EclipseLibrary(Node entry) {
            this.entry = entry
        }

        String getJarName() {
            jarPath.split('/').last()
        }

        String getJarPath() {
            entry.@path
        }

        void assertHasJar(File jar) {
            assert entry.@path == jar.absolutePath.replace(File.separator, '/')
        }

        void assertHasJar(String jar) {
            assert entry.@path == jar
        }

        void assertHasCachedJar(String group, String module, String version) {
            assert entry.@path ==~ cachePath(group, module, version) + Pattern.quote("${module}-${version}.jar")
        }

        void assertHasSource(File jar) {
            assert entry.@sourcepath == jar.absolutePath.replace(File.separator, '/')
        }

        String getSourcePath() {
            entry.@sourcepath
        }

        void assertHasSource(String jar) {
            assert entry.@sourcepath == jar
        }

        void assertHasCachedSource(String group, String module, String version) {
            assert entry.@sourcepath ==~ cachePath(group, module, version) + Pattern.quote("${module}-${version}-sources.jar")
        }

        private String cachePath(String group, String module, String version) {
            return Pattern.quote("${userHomeDir.absolutePath.replace(File.separator, '/')}") + "/caches/${CacheLayout.ROOT.getKey()}/${CacheLayout.FILE_STORE.getKey()}/" + Pattern.quote("${group}/${module}/${version}/") + "\\w+/"
        }

        void assertHasNoSource() {
            assert !entry.@sourcepath
        }

        String getJavadocLocation() {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'javadoc_location'
            entry.attributes[0].attribute[0].@value
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
            assert entry.attributes.isEmpty()
        }

        void assertIsDeployedTo(String path) {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'org.eclipse.jst.component.dependency'
            assert entry.attributes[0].attribute[0].@value == path
        }

        void assertIsExcludedFromDeployment() {
            assert entry.attributes
            assert entry.attributes[0].attribute[0].@name == 'org.eclipse.jst.component.nondependency'
            assert entry.attributes[0].attribute[0].@value == ''
        }

        void assertHasNoDeploymentAttributes() {
            assert entry.attributes.isEmpty()
        }

        void assertExported() {
            assert entry.@exported == 'true'
        }

        void assertNotExported() {
            assert !entry.@exported
        }
    }
}

