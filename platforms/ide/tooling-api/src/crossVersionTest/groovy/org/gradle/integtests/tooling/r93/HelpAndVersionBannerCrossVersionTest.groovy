/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r93

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.VersionBanner

class HelpAndVersionBannerCrossVersionTest extends ToolingApiSpecification {

    @TargetGradleVersion("current")
    @ToolingApiVersion(">=9.3")
    def "current Gradle exposes Help and VersionBanner content"() {
        when:
        def help = withConnection { it.getModel(Help) }
        def env = withConnection { it.getModel(BuildEnvironment) }
        def banner = withConnection { it.getModel(VersionBanner) }

        then:
        help.helpOutput.contains("USAGE:")
        env.gradle.versionOutput.contains("Gradle ")
        banner.versionOutput.contains("Gradle ")
    }

    @TargetGradleVersion("<9.3")
    def "older Gradle does not support new models"() {
        when:
        withConnection { it.getModel(Help) }

        then:
        thrown(UnknownModelException)

        when:
        withConnection { it.getModel(VersionBanner) }

        then:
        thrown(UnknownModelException)

        when:
        withConnection { it.getModel(BuildEnvironment) }

        then:
        def env = withConnection { it.getModel(BuildEnvironment) }
        env != null && env.gradle != null

        when:
        // Calling the new accessor on older providers should raise an UnsupportedMethodException
        env.gradle.versionOutput

        then:
        thrown(UnsupportedMethodException)
    }
}
