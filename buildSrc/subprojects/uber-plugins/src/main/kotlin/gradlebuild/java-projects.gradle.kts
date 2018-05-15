/*
 * Copyright 2018 the original author or authors.
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
package gradlebuild

apply {
    plugin("gradlebuild.unittest-and-compile")
    plugin("gradlebuild.test-fixtures")
    plugin("gradlebuild.distribution-testing")
    plugin("gradlebuild.int-test-image")

    if (file("src/integTest").isDirectory) {
        plugin("gradlebuild.integration-tests")
    }

    if (file("src/crossVersionTest").isDirectory) {
        plugin("gradlebuild.cross-version-tests")
    }

    if (file("src/performanceTest").isDirectory) {
        plugin("gradlebuild.performance-test")
    }

    if (file("src/jmh").isDirectory) {
        plugin("gradlebuild.jmh")
    }
}

tasks.getByName("check") {
    dependsOn(":docs:checkstyleApi")
    dependsOn("codeQuality")
}
