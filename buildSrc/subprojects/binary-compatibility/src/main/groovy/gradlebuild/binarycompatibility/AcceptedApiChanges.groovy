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

package gradlebuild.binarycompatibility

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.util.GradleVersion

class AcceptedApiChanges {

    GradleVersion baseVersion
    Map<ApiChange, String> acceptedChanges

    static AcceptedApiChanges parse(String jsonText) {
        def acceptedApiChanges = new AcceptedApiChanges()
        def json = new Gson().fromJson(jsonText, new TypeToken<Map<String, List<AcceptedApiChange>>>() {}.type)
        acceptedApiChanges.acceptedChanges = json.acceptedApiChanges.collectEntries { jsonChange ->
            [(jsonChange.toApiChange()): jsonChange.acceptation]
        }
        return acceptedApiChanges
    }

    Map<String, String> toAcceptedChangesMap() {
        acceptedChanges.collectEntries { change ->
            [(new Gson().toJson(change.key)): change.value]
        }
    }

    static Map<ApiChange, String> fromAcceptedChangesMap(Map<String, String> acceptedChanges) {
        acceptedChanges.collectEntries { key, value ->
            [(new Gson().fromJson(key, ApiChange)): value]
        }
    }
}
