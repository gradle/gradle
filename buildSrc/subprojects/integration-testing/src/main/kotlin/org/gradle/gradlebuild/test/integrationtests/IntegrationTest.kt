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

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.Named
import org.gradle.api.file.ProjectLayout
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
import java.util.concurrent.Callable


/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
open class IntegrationTest : DistributionTest() {

    @Internal
    val userguideSamples = UserguideSamples(project.layout)

    init {
        jvmArgumentProviders.add(UserguideIntegrationTestEnvironmentProvider(userguideSamples))
        dependsOn(Callable { if (userguideSamples.required) ":docs:extractSamples" else null })
    }
}


class UserguideSamples(layout: ProjectLayout) {

    @Input
    var required = false

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    val samplesXml = layout.fileProperty()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val userGuideSamplesOutput = layout.directoryProperty()
}


class UserguideIntegrationTestEnvironmentProvider(private val samplesInternal: UserguideSamples) : CommandLineArgumentProvider, Named {

    @get:Nested
    @get:Optional
    val samples
        get() =
            if (samplesInternal.required) samplesInternal
            else null

    override fun asArguments() =
        if (samplesInternal.required) {
            mapOf(
                "integTest.userGuideInfoDir" to samplesInternal.samplesXml.asFile.get().parentFile.absolutePath,
                "integTest.userGuideOutputDir" to samplesInternal.userGuideSamplesOutput.asFile.get().absolutePath
            ).asSystemPropertyJvmArguments()
        } else {
            emptyList()
        }

    override fun getName() =
        "userguide"
}
