/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.binarycompatibility

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion

class AcceptedApiChanges {

    GradleVersion baseVersion
    Map<ApiChange, String> acceptedChanges

    static AcceptedApiChanges parse(String jsonText) {
        def json = new JsonSlurper().parseText(jsonText)
        def acceptedApiChanges = new AcceptedApiChanges()
        acceptedApiChanges.acceptedChanges = json.acceptedApiChanges.collectEntries { jsonChange ->
            [(ApiChange.parse(jsonChange)): jsonChange.acceptation]
        }
        return acceptedApiChanges
    }

    Map<String, String> toAcceptedChangesMap() {
        acceptedChanges.collectEntries { change ->
            [(JsonOutput.toJson(change.key)): change.value]
        }
    }

    static Map<ApiChange, String> fromAcceptedChangesMap(Map<String, String> acceptedChanges) {
        acceptedChanges.collectEntries { key, value ->
            [(ApiChange.parse(new JsonSlurper().parseText(key))): value]
        }
    }

}
