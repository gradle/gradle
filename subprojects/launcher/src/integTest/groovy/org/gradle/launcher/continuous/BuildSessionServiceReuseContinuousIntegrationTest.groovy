/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import spock.lang.Unroll


class BuildSessionServiceReuseContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    def cleanup() {
        gradle.cancel()
    }

    @Unroll
    def "reuses #service across continuous builds" () {
        def triggerFileName = "trigger"
        def triggerFile = file(triggerFileName).createFile()
        def idFileName = "build/${service}.id"
        def idFile = file(idFileName).createFile()
        buildFile << """
            import org.gradle.cache.CacheRepository
            import org.gradle.api.internal.ClassPathRegistry
            import org.gradle.process.internal.child.WorkerProcessClassPathProvider
            import org.gradle.process.internal.WorkerProcessBuilder

            task captureService {
                inputs.file file("$triggerFileName")
                doLast {
                    def idFile = file("${idFileName}")
                    mkdir(idFile.parent)
                    def service = services.${method}(${service})
                    idFile << System.identityHashCode(service) + "\\n"
                }
            }
        """

        when:
        triggerFile << "content"

        then:
        succeeds("captureService")

        when:
        triggerFile << "change"

        then:
        succeeds()

        and:
        def ids = idFile.readLines()
        ids.size() == 2
        ids[0] == ids[1]

        where:
        service                          | method
        "WorkerProcessBuilder"           | "getFactory"
        "CacheRepository"                | "get"
        "WorkerProcessClassPathProvider" | "get"
    }
}
