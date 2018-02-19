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
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
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
        samplesXml = project.layout.fileProperty()
        userGuideOutputDir = project.layout.directoryProperty()
        jvmArgumentProviders.add(new IntegrationTestEnvironmentProvider(this))
        dependsOn { requiresSamples ? ':docs:extractSamples' : null }
    }

    @Input
    boolean requiresSamples

    @Internal
    final RegularFileProperty samplesXml

    @Internal
    final DirectoryProperty userGuideOutputDir
}

@CompileStatic
class IntegrationTestEnvironmentProvider implements CommandLineArgumentProvider {
    private final IntegrationTest test

    IntegrationTestEnvironmentProvider(IntegrationTest test) {
        this.test = test
        def project = test.project
        samplesXmlFile = project.provider {
            test.requiresSamples ? test.samplesXml.getOrNull() : null
        }
        userGuide = project.provider {
            test.requiresSamples ? test.userGuideOutputDir.getOrNull() : null
        }
    }
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    final Provider<RegularFile> samplesXmlFile

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<Directory> userGuide

    @Override
    Iterable<String> asArguments() {
        def systemProperties = [:]
        if (test.requiresSamples) {
            systemProperties['integTest.userGuideInfoDir'] = test.samplesXml.asFile.get().parentFile.absolutePath
            systemProperties['integTest.userGuideOutputDir'] = test.userGuideOutputDir.asFile.get().absolutePath
        }
        DistributionTest.asSystemPropertyJvmArguments(systemProperties)
    }
}

