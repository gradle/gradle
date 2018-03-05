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

import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
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
    val userguideSamples: UserguideSamples = UserguideSamples(project.layout)

    init {
        jvmArgumentProviders.add(UserguideIntegrationTestEnvironmentProvider(userguideSamples))
        dependsOn(Callable { if (userguideSamples.required) ":docs:extractSamples" else null })
    }
}

class UserguideSamples(layout: ProjectLayout) {

    @Input
    var required: Boolean = false

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    val samplesXml: RegularFileProperty = layout.fileProperty()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val userGuideSamplesOutput: DirectoryProperty = layout.directoryProperty()

}

class UserguideIntegrationTestEnvironmentProvider(private val samplesInternal: UserguideSamples) : CommandLineArgumentProvider, Named {

    @Nested
    @Optional
    fun getSamples(): UserguideSamples? = if (samplesInternal.required) samplesInternal else null

    override fun asArguments(): Iterable<String> =
        if (samplesInternal.required)
            mapOf(
                "integTest.userGuideInfoDir" to samplesInternal.samplesXml.asFile.get().parentFile.absolutePath,
                "integTest.userGuideOutputDir" to samplesInternal.userGuideSamplesOutput.asFile.get().absolutePath
            ).asSystemPropertyJvmArguments()
        else
            listOf()

    override fun getName(): String = "userguide"
}

