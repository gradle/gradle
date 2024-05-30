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

package gradlebuild.packaging.transforms

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import java.util.zip.ZipFile


// TODO This should work via filtering classes dirs, but for that we need to be able to set a default attribute
//      on classes directories -- see https://github.com/gradle/gradle/issues/29319
@DisableCachingByDefault(because = "Only copies public API classes")
abstract class CopyPublicApiClassesTransform : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jarFile = inputArtifact.get().asFile
        val zipFile = ZipFile(jarFile)
        val outputRoot = outputs.dir("public-api")
        zipFile.stream().forEach { entry ->
            if (entry.name.endsWith(".class")) {
                val packageName = entry.name.substringBeforeLast('/').replace('/', '.') + "."
                if (packageName.startsWith("org.gradle.")) {
                    val outputFile = outputRoot.resolve(entry.name)
                    outputFile.parentFile.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
