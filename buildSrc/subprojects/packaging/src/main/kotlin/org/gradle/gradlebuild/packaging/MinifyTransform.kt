/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.packaging

import org.gradle.api.artifacts.transform.ArtifactTransform

import com.google.common.io.Files

import javax.inject.Inject
import java.io.File


open class MinifyTransform @Inject constructor(
    private val keepClassesByArtifact: Map<String, Set<String>>
) : ArtifactTransform() {

    override fun transform(artifact: File) =
        keepClassesByArtifact.asSequence()
            .firstOrNull { (key, _) -> artifact.name.startsWith(key) }
            ?.value?.let { keepClasses -> listOf(minify(artifact, keepClasses)) }
            ?: listOf(artifact)

    private
    fun minify(artifact: File, keepClasses: Set<String>): File {
        val jarFile = outputDirectory.resolve("${Files.getNameWithoutExtension(artifact.path)}-min.jar")
        val classesDir = outputDirectory.resolve("classes")
        val analysisFile = outputDirectory.resolve("analysis.txt")
        ShadedJarCreator(setOf(artifact), jarFile, analysisFile, classesDir, "", keepClasses, keepClasses, hashSetOf())
            .createJar()
        return jarFile
    }
}
