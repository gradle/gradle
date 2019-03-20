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

apply(plugin = "gradlebuild.unittest-and-compile")
apply(plugin = "gradlebuild.test-fixtures")
apply(plugin = "gradlebuild.distribution-testing")
apply(plugin = "gradlebuild.int-test-image")
apply(plugin = "gradlebuild.incubation-report")

if (file("src/integTest").isDirectory) {
    apply(plugin = "gradlebuild.integration-tests")
}

if (file("src/crossVersionTest").isDirectory) {
    apply(plugin = "gradlebuild.cross-version-tests")
}

if (file("src/performanceTest").isDirectory) {
    apply(plugin = "gradlebuild.performance-test")
}

if (file("src/jmh").isDirectory) {
    apply(plugin = "gradlebuild.jmh")
}

tasks.named("check").configure {
    dependsOn(":docs:checkstyleApi")
    dependsOn("codeQuality")
}
