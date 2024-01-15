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

package org.gradle.configurationcache

import org.gradle.api.DefaultTask
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheDebugOption

import static org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.GradleEnvironment
import static org.gradle.configurationcache.fingerprint.ProjectSpecificFingerprint.ProjectFingerprint

class ConfigurationCacheDebugLogIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "logs categorized open/close frame events for state and fingerprint files"() {
        given:
        createDirs("sub")
        settingsFile << """
            rootProject.name = 'root'
            include 'sub'
        """
        buildFile << """
            allprojects {
                task ok { doLast { println('ok!') } }
            }
        """

        when:
        switch (enablement) {
            case CCDebugEnablement.DEBUG_LOG_LEVEL:
                withDebugLogging()
                break
            case CCDebugEnablement.SYSTEM_PROPERTY:
                executer.withArgument "-D$ConfigurationCacheDebugOption.PROPERTY_NAME=true"
                break
            case CCDebugEnablement.GRADLE_PROPERTIES_FILE:
                file('gradle.properties') << "$ConfigurationCacheDebugOption.PROPERTY_NAME=true"
                break
        }

        and:
        configurationCacheRun 'ok'

        then: "fingerprint frame events are logged"
        def events = collectOutputEvents()
        events.contains([profile: "build fingerprint", type: "O", "frame": GradleEnvironment.name])
        events.contains([profile: "build fingerprint", type: "C", "frame": GradleEnvironment.name])
        events.contains([profile: "project fingerprint", type: "O", "frame": ProjectFingerprint.name])
        events.contains([profile: "project fingerprint", type: "C", "frame": ProjectFingerprint.name])

        and: "Gradle and Work Graph events are logged"
        events.contains([profile: "build ':' state", type: "O", frame: "Gradle"])
        events.contains([profile: "build ':' state", type: "O", frame: "Work Graph"])

        and: "state frame events are logged"
        events.contains([profile: "build ':' state", type: "O", frame: ":ok"])
        events.contains([profile: "build ':' state", type: "C", frame: ":ok"])
        events.contains([profile: "build ':' state", type: "O", frame: ":sub:ok"])
        events.contains([profile: "build ':' state", type: "C", frame: ":sub:ok"])

        and: "task type frame follows task path frame follows LocalTaskNode frame"
        def firstTaskNodeIndex = events.findIndexOf { it.frame == LocalTaskNode.name }
        firstTaskNodeIndex > 0
        events[firstTaskNodeIndex] == [profile: "build ':' state", type: "O", frame: LocalTaskNode.name]
        events[firstTaskNodeIndex + 1] == [profile: "build ':' state", type: "O", frame: ":ok"]
        events[firstTaskNodeIndex + 2] == [profile: "build ':' state", type: "O", frame: DefaultTask.name]

        where:
        enablement << CCDebugEnablement.values()
    }

    enum CCDebugEnablement {
        SYSTEM_PROPERTY,
        GRADLE_PROPERTIES_FILE,
        DEBUG_LOG_LEVEL,
    }

    private Collection<Map<String, Object>> collectOutputEvents() {
        def pattern = /\{"profile":"(.*?)","type":"(O|C)","frame":"(.*?)","at":\d+\,"sn":\d+\}/
        (output =~ pattern)
            .findAll()
            .collect { matchResult ->
                //noinspection GroovyUnusedAssignment
                def (ignored, profile, type, frame) = matchResult
                [profile: profile, type: type, frame: frame]
            }
    }
}
