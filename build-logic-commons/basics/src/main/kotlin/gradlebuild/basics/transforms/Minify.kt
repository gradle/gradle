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

package gradlebuild.basics.transforms

import com.google.common.io.Files
import gradlebuild.basics.classanalysis.JarPackager
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity


@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var keepClassesByArtifact: Map<String, Set<String>>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        for (entry in parameters.keepClassesByArtifact) {
            val fileName = artifact.get().asFile.name
            if (fileName.startsWith(entry.key)) {
                val nameWithoutExtension = Files.getNameWithoutExtension(fileName)
                JarPackager().minify(artifact.get().asFile, emptyList(), outputs.file("$nameWithoutExtension-min.jar")) {
                    keepClasses(entry.value)
                }
                return
            }
        }
        outputs.file(artifact)
    }
}
