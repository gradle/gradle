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

import org.eclipse.jetty.servlet.FilterHolder
import org.gradle.util.GradleVersion

import javax.servlet.DispatcherType
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

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

        httpBuildCacheServer.customHandler.addFilter(new FilterHolder(new Filter() {
            @Override
            void init(FilterConfig filterConfig) throws ServletException {}

            @Override
            void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                assert request.getHeader("X-Gradle-Version") == GradleVersion.current().version
                chain.doFilter(request, response)
            }

            @Override
            void destroy() {}
        }), "/*", EnumSet.of(DispatcherType.REQUEST))

        settingsFile << withHttpBuildCacheServer()

        expect:
        withBuildCache().succeeds "compileJava"

        when:
        withBuildCache().run "clean"
        then:
        withBuildCache().succeeds "compileJava"
    }
}
