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

package gradlebuild.shade.transforms

import com.google.gson.Gson
import gradlebuild.basics.classanalysis.JarAnalyzer
import gradlebuild.identity.tasks.BuildReceipt
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


private
const val classTreeFileName = "classTree.json"


private
const val entryPointsFileName = "entryPoints.json"


private
const val relocatedClassesDirName = "classes"


private
const val manifestFileName = "MANIFEST.MF"


@CacheableTransform
abstract class ShadeClasses : TransformAction<ShadeClasses.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var shadowPackage: String
        @get:Input
        var keepPackages: Set<String>
        @get:Input
        var unshadedPackages: Set<String>
        @get:Input
        var ignoredPackages: Set<String>
    }

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputDirectory = outputs.dir("shadedClasses")
        val classesDir = outputDirectory.resolve(relocatedClassesDirName)
        classesDir.mkdir()
        val manifestFile = outputDirectory.resolve(manifestFileName)
        val buildReceiptFile = outputDirectory.resolve(BuildReceipt.buildReceiptFileName)

        val classGraph = JarAnalyzer(parameters.shadowPackage, parameters.keepPackages, parameters.unshadedPackages, parameters.ignoredPackages).analyze(input.get().asFile, classesDir, manifestFile, buildReceiptFile)

        outputDirectory.resolve(classTreeFileName).bufferedWriter().use {
            Gson().toJson(classGraph.getDependencies(), it)
        }
        outputDirectory.resolve(entryPointsFileName).bufferedWriter().use {
            Gson().toJson(classGraph.entryPoints.map { it.outputClassFilename }, it)
        }
    }
}


abstract class FindClassTrees : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.file(input.get().asFile.resolve(classTreeFileName))
    }
}


abstract class FindEntryPoints : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.file(input.get().asFile.resolve(entryPointsFileName))
    }
}


abstract class FindRelocatedClasses : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        outputs.dir(input.get().asFile.resolve(relocatedClassesDirName))
    }
}


abstract class FindManifests : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val manifest = input.get().asFile.resolve(manifestFileName)
        if (manifest.exists()) {
            outputs.file(manifest)
        }
    }
}


abstract class FindBuildReceipt : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val manifest = input.get().asFile.resolve(BuildReceipt.buildReceiptFileName)
        if (manifest.exists()) {
            outputs.file(manifest)
        }
    }
}
