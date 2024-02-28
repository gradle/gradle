/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.language.scala.fixtures

import org.gradle.test.fixtures.file.TestFile

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class ScalaJarFactory {

    final TestFile testDirectory

    ScalaJarFactory(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    TestFile standard(String module, String version) {
        def scala3 = version.startsWith("3.")
        custom(module, scala3, version, scala3 ? null : version, scala3 ? version : null)
    }

    TestFile custom(String module, boolean scala3, String versionInName, String versionInProperties, String versionInManifest) {
        def fileName = (scala3 ? "scala3-${module}_3" : "scala-${module}") + (versionInName != null ? "-${versionInName}" : "") + ".jar"
        def file = testDirectory.file(fileName).createFile()
        file.withOutputStream { fileStream ->
            def manifest = new Manifest()
            manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            if (versionInManifest != null) {
                manifest.mainAttributes.put(Attributes.Name.IMPLEMENTATION_VERSION, versionInManifest)
            }
            try (def jarStream = new JarOutputStream(fileStream, manifest)) {
                if (versionInProperties != null) {
                    def properties = new Properties()
                    properties.put("maven.version.number", versionInProperties)
                    jarStream.putNextEntry(new JarEntry("${module}.properties"))
                    properties.store(new BufferedOutputStream(jarStream), null)
                    jarStream.closeEntry()
                }
            }
        }
        file
    }
}
