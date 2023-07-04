/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.basics.testing

import org.gradle.api.tasks.testing.Test


enum class TestType(val prefix: String, val executers: List<String>) {
    INTEGRATION("integ", listOf("embedded", "forking", "noDaemon", "parallel", "configCache", "projectIsolation")),
    CROSSVERSION("crossVersion", listOf("embedded", "forking"))
}


fun Test.includeSpockAnnotation(fqcn: String) {
    systemProperties.compute("include.spock.annotation") { _, oldValue ->
        if (oldValue == null) fqcn else "$oldValue,$fqcn"
    }
}


fun Test.excludeSpockAnnotation(fqcn: String) {
    systemProperties.compute("exclude.spock.annotation") { _, oldValue ->
        if (oldValue == null) fqcn else "$oldValue,$fqcn"
    }
}
