/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.api.Action
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.internal.agents.AgentStatus
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.GradleVersion
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static org.apache.commons.io.FileUtils.touch

class DefaultGeneratedGradleJarCacheIntegrationTest extends Specification {
    private final static String CACHE_IDENTIFIER = 'test'
    private final static long JAR_GENERATION_TIME_MS = 2000L

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @Rule
    RedirectStdOutAndErr stdout = new RedirectStdOutAndErr()

    @Rule
    ConcurrentTestUtil concurrent = new ConcurrentTestUtil(8000)

    def services = (DefaultServiceRegistry) ServiceRegistryBuilder.builder()
            .parent(NativeServicesTestFixture.getInstance())
            .provider(LoggingServiceRegistry.NO_OP)
            .provider(new GlobalScopeServices(false, AgentStatus.disabled()))
            .build()

    def factory = services.get(CacheFactory.class)
    def currentGradleVersion = GradleVersion.current()
    def scopeMapping = new DefaultCacheScopeMapping(tmpDir.testDirectory, currentGradleVersion)
    def cacheRepository = new DefaultUnscopedCacheBuilderFactory(scopeMapping, factory)
    def globalScopedCache = new DefaultGlobalScopedCacheBuilderFactory(tmpDir.testDirectory, cacheRepository)
    def defaultGeneratedGradleJarCache = new DefaultGeneratedGradleJarCache(globalScopedCache, currentGradleVersion.getVersion())

    def cleanup() {
        defaultGeneratedGradleJarCache.close()
    }

    def "locks cache upon JAR creation"() {
        given:
        def startSecondInvocation = new CountDownLatch(1)
        def jarFile1
        def jarFile2

        when:
        concurrent.start {
            jarFile1 = defaultGeneratedGradleJarCache.get(CACHE_IDENTIFIER, new Action<File>() {
                @Override
                void execute(File file) {
                    startSecondInvocation.countDown()
                    Thread.sleep(JAR_GENERATION_TIME_MS)
                    touch(file)
                }
            })
        }

        concurrent.start {
            startSecondInvocation.await()
            jarFile2 = defaultGeneratedGradleJarCache.get(CACHE_IDENTIFIER, new Action<File>() {
                @Override
                void execute(File file) {
                    throw new IllegalStateException('Cache does not lock properly')
                }
            })
        }

        concurrent.finished()

        then:
        noExceptionThrown()
        jarFile1 && jarFile2
        jarFile1.exists() && jarFile2.exists()
        jarFile1 == jarFile2
    }

    def "only generates single JAR in cache when invoked by concurrent threads"() {
        given:
        def jarGenerationInvocations = 10
        def triggeredJarFileGeneration = new AtomicInteger()
        def jarFiles = Collections.synchronizedList(new ArrayList<File>())

        when:
        jarGenerationInvocations.times {
            concurrent.start {
                def jarFile = defaultGeneratedGradleJarCache.get(CACHE_IDENTIFIER, new Action<File>() {
                    @Override
                    void execute(File file) {
                        triggeredJarFileGeneration.incrementAndGet()
                        Thread.sleep(JAR_GENERATION_TIME_MS)
                        touch(file)
                    }
                })

                synchronized(jarFiles) {
                    jarFiles << jarFile
                }
            }
        }

        concurrent.finished()

        then:
        triggeredJarFileGeneration.get() == 1
        jarFiles.size() == jarGenerationInvocations
        jarFiles.unique().size() == 1
    }
}
