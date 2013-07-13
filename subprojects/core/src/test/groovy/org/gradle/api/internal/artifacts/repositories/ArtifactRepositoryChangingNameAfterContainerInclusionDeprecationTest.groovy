/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestAppender
import org.gradle.util.DeprecationLogger
import org.gradle.util.HelperUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class ArtifactRepositoryChangingNameAfterContainerInclusionDeprecationTest extends Specification {

    TestAppender appender = new TestAppender()
    @Rule ConfigureLogging logging = new ConfigureLogging(appender)
    Project project

    def setup() {
        project = HelperUtil.createRootProject()
        DeprecationLogger.reset()
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    /**
     * This is a bit of a weird test. We are assuming that repository impls are extending AbstractArtifactRepository.
     * Also, we are relying on DefaultReportContainerTest testing that we inform repositories when they are
     */
    @Unroll
    def "logs deprecation warning on name change of #name repo"() {
        given:
        ArtifactRepository artifactRepository = repoNotation.call(project.repositories)

        when:
        artifactRepository.name = "changed"

        then:
        appender.toString().contains("Changing the name of an ArtifactRepository that is part of a container has been deprecated")

        where:
        name           | repoNotation
        "flatDir"      | { it.flatDir {} }
        "ivy"          | { it.ivy {} }
        "maven"        | { it.maven {} }
        "mavenCentral" | { it.mavenCentral() }
        "mavenLocal"   | { it.mavenLocal() }
    }
}
