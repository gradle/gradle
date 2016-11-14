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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
@CompileStatic
class IntegrationTest extends DistributionTest {

    IntegrationTest() {
        dependsOn { requiresSamples ? ':docs:extractSamples' : null }
    }

    @Input
    boolean requiresSamples

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    File samplesXml

    void setSamplesXml(File samplesXml) {
        this.samplesXml = samplesXml
        fileSystemProperty('integTest.userGuideInfoDir', samplesXml.parentFile)
    }

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File userGuideOutputDir

    void setUserGuideOutputDir(File userGuideOutputDir) {
        this.userGuideOutputDir = userGuideOutputDir
        fileSystemProperty('integTest.userGuideOutputDir', userGuideOutputDir)
    }
}
