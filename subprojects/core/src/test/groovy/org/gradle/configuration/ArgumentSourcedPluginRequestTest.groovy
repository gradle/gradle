/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configuration

import org.gradle.plugin.management.internal.argumentloaded.ArgumentSourcedPluginRequest
import spock.lang.Specification

class ArgumentSourcedPluginRequestTest extends Specification {
    def "can parse plugin id"() {
        when:
        ArgumentSourcedPluginRequest request = ArgumentSourcedPluginRequest.parsePluginRequest("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        request.getId().toString() == "org.barfuin.gradle.taskinfo"
        request.getVersion() == "2.2.0"
    }

    def "reports error if invalid arg provided: #coords"() {
        when:
        ArgumentSourcedPluginRequest.parsePluginRequest(coords)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Invalid plugin format: '$coords'. Expected format is 'id:version'."

        where:
        coords << ["", " ", "nonsense", ":more:nonsense", ":incorrect", "incorrect:", ":", "org.gradle:1.0.0:1.0.0"]
    }
}
