/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.classpath

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

public class ManifestUtilTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def jarFile = tmpDir.file("mydir/jarfile.jar").createFile()

    def "creates manifest classpath with relative urls"() {
        when:
        def classpathFiles = [tmpDir.file('mydir/jar1.jar'), tmpDir.file('mydir/nested/jar2.jar')]

        then:
        ManifestUtil.createManifestClasspath(jarFile, classpathFiles) == "jar1.jar nested/jar2.jar";
    }

    def "creates manifest classpath with absolute urls"() {
        when:
        def tmpDirPath = tmpDir.testDirectory.toURI().rawPath
        def file1 = tmpDir.file('different/jar1.jar')
        def file2 = tmpDir.file('different/nested/jar2.jar')

        then:
        ManifestUtil.createManifestClasspath(jarFile, [file1, file2]) == "${tmpDirPath}different/jar1.jar ${tmpDirPath}different/nested/jar2.jar"
    }

    def "url encodes spaces in manifest classpath"() {
        when:
        def classpathFiles = [tmpDir.file('mydir/jar one.jar'), tmpDir.file('mydir/nested dir/jar two.jar')]

        then:
        ManifestUtil.createManifestClasspath(jarFile, classpathFiles) == "jar%20one.jar nested%20dir/jar%20two.jar";
    }

    def "returns empty classpath list for missing file or directory"() {
        when:
        def nonexistent = new File("does no exist");
        def directory = tmpDir.createDir("new_directory");

        then:
        ManifestUtil.parseManifestClasspath(nonexistent) == []
        ManifestUtil.parseManifestClasspath(directory) == []
    }

    def "returns empty classpath for non-jar file"() {
        when:
        def file = tmpDir.createFile('non-jar.zip')
        file << "text"

        then:
        ManifestUtil.parseManifestClasspath(file) == []
    }

    def "returns empty classpath for jar without manifest"() {
        when:
        createJar()

        then:
        ManifestUtil.parseManifestClasspath(jarFile) == []
    }

    def "returns empty classpath for jar with manifest without Class-Path"() {
        when:
        createJar(manifestWithClasspath(null))

        then:
        ManifestUtil.parseManifestClasspath(jarFile) == []
    }

    def "returns empty classpath for jar with manifest with empty Class-Path"() {
        when:
        createJar(manifestWithClasspath(""))

        then:
        ManifestUtil.parseManifestClasspath(jarFile) == []
    }

    def "returns classpath for jar with manifest"() {
        when:
        createJar(manifestWithClasspath('foo.jar'))

        then:
        ManifestUtil.parseManifestClasspath(jarFile) == [new File(jarFile.parentFile, 'foo.jar').toURI()]
    }

    def "returned classpath URI is absolute and can be used to locate file"() {
        when:
        def classpathFile = tmpDir.createFile("mydir/foo.jar")
        createJar(manifestWithClasspath('foo.jar'))

        and:
        def classpathURI = ManifestUtil.parseManifestClasspath(jarFile)[0]

        then:
        new File(classpathURI).absoluteFile == classpathFile.absoluteFile
    }

    private def createJar(def manifest = null) throws IOException {
        def jarOutputStream

        if (manifest == null) {
            jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile))
        } else {
            jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile), manifest)
        }

        jarOutputStream.putNextEntry(new ZipEntry("META-INF/"));
        jarOutputStream.close();
    }

    private def manifestWithClasspath(def manifestClasspath) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (manifestClasspath != null) {
            attributes.putValue("Class-Path", manifestClasspath);
        }
        return manifest
    }

}
