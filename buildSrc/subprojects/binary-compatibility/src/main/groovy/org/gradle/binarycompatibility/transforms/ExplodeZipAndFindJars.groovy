/*
 * Copyright 2017 the original author or authors.
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


package org.gradle.binarycompatibility.transforms

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.transform.ArtifactTransform

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.nio.file.Files

@CompileStatic
class ExplodeZipAndFindJars extends ArtifactTransform {

    @Override
    List<File> transform(final File file) {
        List<File> result = []
        if (outputDirectory.exists() && outputDirectory.listFiles().length == 0) {
            File gradleJars = new File(outputDirectory, "gradle-jars")
            File dependencies = new File(outputDirectory, "gradle-dependencies")
            gradleJars.mkdir()
            dependencies.mkdir()
            result << gradleJars
            result << dependencies
            ZipInputStream zin = new ZipInputStream(file.newInputStream())
            ZipEntry zipEntry
            while (zipEntry = zin.nextEntry) {
                String shortName = zipEntry.name
                if (shortName.contains('/')) {
                    shortName = shortName.substring(shortName.lastIndexOf('/') + 1)
                }
                if (shortName.endsWith('.jar')) {
                    def outputDir = shortName.startsWith('gradle-') ? gradleJars : dependencies
                    def out = new File(outputDir, shortName)
                    Files.copy(zin, out.toPath())
                    zin.closeEntry()
                }
            }
        }
        result
    }
}
