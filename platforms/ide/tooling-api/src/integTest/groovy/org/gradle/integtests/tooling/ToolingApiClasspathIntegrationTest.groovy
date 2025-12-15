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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.util.internal.TextUtil

class ToolingApiClasspathIntegrationTest extends AbstractIntegrationSpec {

    def "tooling api classpath contains only slf4j and shaded tooling-api jar of expected size"() {
        IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
        buildFile << """
            plugins {
                id("java-library")
            }

            // For the current tooling API jar
            repositories {
                maven {
                    url = uri(file("${TextUtil.escapeString(buildContext.localRepository)}"))
                }
            }

            // For SFL4J
            repositories {
                ${RepoScriptBlockUtil.gradleRepositoryDefinition()}
            }

            dependencies {
                implementation("org.gradle:gradle-tooling-api:${distribution.getVersion().baseVersion.version}")
            }

            tasks.register("resolve") {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    // If either of these expectations change,
                    // `ToolingApiDistributionResolver` must be updated.
                    assert files.size() == 2
                    assert files.find { it.name ==~ /slf4j-api-.*\\.jar/ } != null

                    def shadedTapiJar = files.find { it.name ==~ /gradle-tooling-api.*\\.jar/ }
                    assert shadedTapiJar != null
                    println "SHADED_TAPI_JAR_SIZE: " + shadedTapiJar.size() + " bytes"
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        def actualSize = extractShadedTapiJarSize(output)
        def actualSizeKB = (int) Math.ceil((double) actualSize / 1024)

        def expectedSizeKB = 2950
        def marginKB = 50

        def message = { smaller ->
            def changed = smaller == "smaller" ? "removed" : "added"
            "Shaded TAPI jar is unexpectedly ${smaller} and needs to be verified." +
                "\nCurrent size: ${actualSizeKB} KiB. Expected size: ${expectedSizeKB} ± ${marginKB} KiB." +
                "\nThe shaded jar is produced via tree-shaking. If this suddenly fails without an obvious reason, you likely have ${changed} some dependencies between classes."
        }

        assert actualSizeKB >= (expectedSizeKB - marginKB): message("smaller")
        assert actualSizeKB <= (expectedSizeKB + marginKB): message("larger")
    }

    private static long extractShadedTapiJarSize(String output) {
        def matcher = output =~ /SHADED_TAPI_JAR_SIZE: (\d+) bytes/
        assert matcher.find(): "Could not find shaded TAPI jar size in output"
        return matcher.group(1) as long
    }
}
