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

package org.gradle.testing

import groovy.transform.CompileStatic
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
@CompileStatic
class IntegrationTest extends DistributionTest {
    IntegrationTest() {
        userguideSamples = new UserguideSamples(project.layout)
        jvmArgumentProviders.add(new SamplesIntegrationTestEnvironmentProvider(userguideSamples))
        dependsOn { userguideSamples.required ? ':docs:extractSamples' : null }
    }

    @Internal
    final UserguideSamples userguideSamples
}

class UserguideSamples {

    UserguideSamples(ProjectLayout layout) {
        samplesXml = layout.fileProperty()
        userGuideSamplesOutput = layout.directoryProperty()
    }

    @Input
    boolean required

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    final RegularFileProperty samplesXml

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty userGuideSamplesOutput
}

@CompileStatic
class SamplesIntegrationTestEnvironmentProvider implements CommandLineArgumentProvider {
    private final UserguideSamples userguideSamples

    SamplesIntegrationTestEnvironmentProvider(UserguideSamples userguideSamples) {
        this.userguideSamples = userguideSamples
    }

    @Nested
    @Optional
    UserguideSamples getUserguideSamples() {
        userguideSamples.required ? userguideSamples : null
    }

    @Override
    Iterable<String> asArguments() {
        DistributionTest.asSystemPropertyJvmArguments(
            userguideSamples.required ?
                [
                    'integTest.userGuideInfoDir'  : userguideSamples.samplesXml.asFile.get().parentFile.absolutePath,
                    'integTest.userGuideOutputDir': userguideSamples.userGuideSamplesOutput.asFile.get().absolutePath
                ] : [:]
        )
    }
}

