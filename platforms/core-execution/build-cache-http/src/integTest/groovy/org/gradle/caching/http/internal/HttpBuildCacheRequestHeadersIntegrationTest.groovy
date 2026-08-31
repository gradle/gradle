/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.caching.http.internal

import org.gradle.util.GradleVersion

class HttpBuildCacheRequestHeadersIntegrationTest extends HttpBuildCacheFixture {
    def "sends X-Gradle-Version header with store and load requests"() {
        given:
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """
        file("src/main/resources/resource.properties") << """
            test=true
        """

        httpBuildCacheServer.addResponder { request, response ->
            assert request.getHeader("X-Gradle-Version") == GradleVersion.current().version
            true
        }

        settingsFile << withHttpBuildCacheServer()

        expect:
        withBuildCache().succeeds "compileJava"

        when:
        withBuildCache().run "clean"
        then:
        withBuildCache().succeeds "compileJava"
    }
}
