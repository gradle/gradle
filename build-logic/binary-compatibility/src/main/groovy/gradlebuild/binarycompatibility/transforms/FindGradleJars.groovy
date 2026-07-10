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

import gradlebuild.basics.PublicApiVariants
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

@CompileStatic
@DisableCachingByDefault(because = "Only filters the input artifact")
abstract class FindGradleJars implements TransformAction<TransformParameters.None> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    abstract Provider<FileSystemLocation> getArtifact()

    @Override
    void transform(TransformOutputs outputs) {
        File baselineJarsDirectory = artifact.get().asFile
        if (baselineJarsDirectory.name == 'gradle-jars') {
            baselineJarsDirectory.listFiles().each {
                // Skip the aggregated `gradle-public-api-legacy` ABI-stub jar (introduced in 9.7,
                // shipped in the distribution's `lib/api/`). It is a second copy of every public API
                // class, produced by the API extractor with a different (legacy) configuration. The
                // "current" side of the comparison uses the per-module jars and never includes it, so
                // including it here puts every public class in the baseline twice. japicmp resolves
                // duplicate class names last-wins, fed by an unordered File.listFiles(), so it would
                // non-deterministically compare against the legacy ABI stub and report spurious
                // binary-compatibility violations. Excluding it keeps the baseline per-module, which
                // matches the current side and the pre-9.7 behaviour.
                if (!it.name.startsWith(PublicApiVariants.LEGACY_MODULE_NAME)) {
                    outputs.file(it)
                }
            }
        }
    }
}
