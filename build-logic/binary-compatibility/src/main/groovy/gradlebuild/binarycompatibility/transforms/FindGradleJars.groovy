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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CompileStatic
abstract class FindGradleJars implements TransformAction<Parameters> {

    @CompileStatic
    interface Parameters extends TransformParameters {
        @InputFiles
        ConfigurableFileCollection getCurrentJars()

        @Input
        Property<String> getCurrentVersion()
    }

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    abstract Provider<FileSystemLocation> getArtifact()

    @Override
    void transform(TransformOutputs outputs) {
        File baselineJarsDirectory = artifact.get().asFile
        if (baselineJarsDirectory.name == 'gradle-jars') {
            def jarPrefixes = parameters.currentJars.collect { it.name.replace("${parameters.currentVersion.get()}.jar", '') }
            baselineJarsDirectory.listFiles().each {
                for (def jarPrefix : jarPrefixes) {
                    if (it.name.startsWith(jarPrefix)) {
                        outputs.file(it)
                    }
                }
            }
        }
    }
}
