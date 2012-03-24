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

package org.gradle.process.internal.child;

import java.util.jar.Manifest
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

public class UtilityJarFactoryTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    private def UtilityJarFactory factory = new UtilityJarFactory()

    def "creates classpath jar with relative urls in manifest"() {
        def jarFile = tmpDir.file("mydir/jarfile.jar").createFile()
        def classpathFiles = [tmpDir.file('mydir/jar1.jar'), tmpDir.file('mydir/nested/jar2.jar')]

        when:
        factory.createClasspathJarFile(jarFile, classpathFiles);

        then:
        getManifestClassPath(jarFile) == "jar1.jar nested/jar2.jar"
    }

    def "creates classpath jar with absolute urls in manifest"() {
        def jarFile = tmpDir.file("mydir/jarfile.jar").createFile()

        when:
        def tmpDirPath = tmpDir.dir.toURI().rawPath
        def file1 = tmpDir.file('different/jar1.jar')
        def file2 = tmpDir.file('different/nested/jar2.jar')
        factory.createClasspathJarFile(jarFile, [file1, file2]);

        then:
        getManifestClassPath(jarFile) == "${tmpDirPath}different/jar1.jar ${tmpDirPath}different/nested/jar2.jar"
    }

    private def getManifestClassPath(def jarFile) {
        getManifest(jarFile).mainAttributes.getValue('Class-Path')
    }

    private def getManifest(def jarFile) {
        def result = tmpDir.createDir("unzip")
        jarFile.unzipTo(result)
        Manifest manifest = new Manifest(new FileInputStream(result.file('META-INF/MANIFEST.MF')));
        return manifest
    }
}
