/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching

import org.eclipse.jetty.servlet.FilterHolder
import org.gradle.caching.internal.services.NextGenBuildCacheControllerFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.test.fixtures.server.http.HttpBuildCacheServer
import org.junit.Rule

import javax.servlet.DispatcherType
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest

class NextGenBuildCacheHttpIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @Rule
    HttpBuildCacheServer httpBuildCacheServer = new HttpBuildCacheServer(temporaryFolder)

    def setup() {
        httpBuildCacheServer.start()
        settingsFile << """
            buildCache {
                remote(HttpBuildCache) {
                    url = "${httpBuildCacheServer.uri}"
                    push = true
                }
            }
        """
    }

    def "compile task is loaded from cache"() {
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        runWithBuildCacheNG "compileJava"
        then:
        executedAndNotSkipped ":compileJava"

        def alternativeBuildCache = new TestBuildCache(temporaryFolder.file("cache-dir-2").deleteDir().createDir())
        settingsFile << alternativeBuildCache.localCacheConfiguration()

        when:
        runWithBuildCacheNG "clean", "compileJava"
        then:
        skipped ":compileJava"
    }

    def "should use different hashes for same artifacts than production build cache"() {
        def filter = new HashCollectingFilter()
        httpBuildCacheServer.customHandler.addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST))
        buildFile << """
            plugins {
                id("base")
            }

            @CacheableTask
            abstract class BuildCacheTask extends DefaultTask {
                @Input
                abstract Property<String> getInput();
                @OutputFile
                abstract RegularFileProperty getOutput();

                @TaskAction
                void run() {
                    def file = output.asFile.get()
                    file.text = input.get()
                }
            }

            tasks.register("testBuildCache", BuildCacheTask) {
                input.set("Hello world")
                output.set(file("build/build-cache/output.txt"))
            }
        """


        when:
        runWithBuildCache "testBuildCache"

        then:
        executedAndNotSkipped ":testBuildCache"
        def productionHashes = filter.getAndClearArtifactHashes()
        !productionHashes.empty

        when:
        runWithBuildCacheNG "clean", "testBuildCache"

        then:
        executedAndNotSkipped ":testBuildCache"
        def ngHashes = filter.getAndClearArtifactHashes()
        !ngHashes.empty
        ngHashes.intersect(productionHashes).empty
    }

    private runWithBuildCache(String... tasks) {
        withBuildCache().run(tasks)
    }

    private runWithBuildCacheNG(String... tasks) {
        withBuildCache().run("-D${NextGenBuildCacheControllerFactory.NEXT_GEN_CACHE_SYSTEM_PROPERTY}=true", *tasks)
    }

    private class HashCollectingFilter implements Filter {
        private final Set<String> artifactHashes = new LinkedHashSet<>()

        @Override
        void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            def hash = (request as HttpServletRequest).getRequestURI().replace("/", "")
            artifactHashes.add(hash)
            chain.doFilter(request, response)
        }

        @Override
        void destroy() {
        }

        Set<String> getAndClearArtifactHashes() {
            Set<String> artifactHashes = new LinkedHashSet<>(this.artifactHashes)
            this.artifactHashes.clear()
            return artifactHashes
        }
    }
}
