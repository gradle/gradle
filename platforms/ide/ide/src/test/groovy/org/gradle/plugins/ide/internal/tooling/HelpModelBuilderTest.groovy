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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.Project
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.build.VersionInfo
import spock.lang.Specification

class HelpModelBuilderTest extends Specification {
    def builder = new HelpModelBuilder()
    def project = Mock(Project)

    def "can build VersionInfo model"() {
        given:
        project.getRootDir() >> new File(".")

        when:
        def model = builder.buildAll(VersionInfo.class.getName(), project)

        then:
        model instanceof VersionInfo
        // Should contain "Gradle" and version number, indicating CliTextPrinter was used
        model.versionOutput.contains("Gradle")
        model.versionOutput.contains("JVM:") 
    }

    def "can build Help model"() {
        when:
        def model = builder.buildAll(Help.class.getName(), project)

        then:
        model instanceof Help
        // Should contain "USAGE:", indicating CliTextPrinter was used
        model.helpOutput.contains("USAGE:")
    }
}
