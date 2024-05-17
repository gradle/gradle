/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
@Issue("https://github.com/gradle/gradle/issues/18486")
class JarFilePackageListerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final TestFile testDir = tmpDir.testDirectory
    private final TestFile zipFile = testDir.file("lib.jar")
    private final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))

    def lister = new JarFilePackageLister()
    def listener = Mock(JarFilePackageListener)

    def setup() {
        out.withCloseable {
            it.putNextEntry(new ZipEntry("com/a/b/c/"))
            it.putNextEntry(new ZipEntry("com/a/b/c/A.class"))
            it.putNextEntry(new ZipEntry("com/a/b/c/B.class"))
            it.putNextEntry(new ZipEntry("com/a/b/"))
            it.putNextEntry(new ZipEntry("com/a/b/C.class"))
            it.putNextEntry(new ZipEntry("com/"))
            it.putNextEntry(new ZipEntry("com/D.class"))
            it.putNextEntry(new ZipEntry("E.class"))
        }
    }

    def testListenerReceiving() {
        when:
        lister.listJarPackages(zipFile, listener)

        then:
        3 * listener.receivePackage("com/a/b/c/")
        2 * listener.receivePackage("com/a/b/")
        2 * listener.receivePackage("com/")
        1 * listener.receivePackage("")
        0 * listener.receivePackage("*_")
    }
}
