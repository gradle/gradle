/*
 * Copyright 2020 the original author or authors.
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


package gradlebuild.binarycompatibility.transforms

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@CompileStatic
@DisableCachingByDefault(because = "Not worth caching")
abstract class ExplodeZipAndFindJars implements TransformAction<TransformParameters.None> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    abstract Provider<FileSystemLocation> getArtifact()

    @Override
    void transform(TransformOutputs outputs) {
        File gradleJars = outputs.dir("gradle-jars")
        File dependencies = outputs.dir("gradle-dependencies")
        try (ZipInputStream zin = new ZipInputStream(artifact.get().asFile.newInputStream())) {
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
    }
}
