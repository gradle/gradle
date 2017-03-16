/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.modules

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.*

@CacheableTask
@CompileStatic
class PatchExternalModules extends DefaultTask {

    @Internal
    Configuration externalModules

    @Input
    Set<String> getExternalModuleNames() {
        externalModules.dependencies*.name as Set
    }

    @Classpath
    Configuration externalModulesRuntime

    @Classpath
    Configuration coreRuntime

    @OutputDirectory
    File destination

    PatchExternalModules() {
        description = 'Patches the classpath manifests of external modules such as gradle-script-kotlin to match the Gradle runtime configuration.'
    }

    @TaskAction
    void patch() {
        ((ProjectInternal) project).sync { CopySpec copySpec ->
            copySpec.from(externalModulesRuntime - coreRuntime)
            copySpec.into(destination)
        }

        def rootProject = project.rootProject as ProjectInternal

        new ClasspathManifestPatcher(rootProject, temporaryDir, externalModulesRuntime, externalModuleNames)
                .writePatchedFilesTo(destination)

        // TODO: Should this be configurable?
        new ExcludeEntryPatcher(rootProject, temporaryDir, externalModulesRuntime, "kotlin-compiler-embeddable")
                .exclude("META-INF/services/java.nio.charset.spi.CharsetProvider")
                .exclude("net/rubygrapefruit/platform/**")
                .writePatchedFilesTo(destination)
    }
}
