/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.process.ShellScript
import spock.lang.Issue

class ConfigurationCacheInputTrackingIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    @Issue("https://github.com/gradle/gradle/issues/24203")
    def "build cache service can refresh untracked credentials after configuration cache reuse"() {
        given:
        def credentialsProvider = ShellScript.builder().printEnvironmentVariable("CREDENTIALS").writeTo(testDirectory, "credentials-provider")
        def profile = file("credentials.profile")
        profile.text = credentialsProvider.commandLine.join(System.lineSeparator())

        settingsFile << """
            import org.gradle.api.configuration.ConfigurationCacheInputTracking
            import org.gradle.api.provider.ProviderFactory
            import org.gradle.caching.BuildCacheException
            import org.gradle.caching.BuildCacheKey
            import org.gradle.caching.BuildCacheService
            import org.gradle.caching.BuildCacheServiceFactory
            import org.gradle.caching.configuration.AbstractBuildCache

            import javax.inject.Inject

            interface CredentialsProviderFactory extends Serializable {
                CredentialsProvider create()
            }

            class ProfileCredentialsProviderFactory implements CredentialsProviderFactory {
                private final String profilePath

                ProfileCredentialsProviderFactory(String profilePath) {
                    this.profilePath = profilePath
                }

                @Override
                CredentialsProvider create() {
                    return new CredentialsProvider(profilePath)
                }
            }

            class CredentialsProvider {
                private final List<String> commandLine
                private final Object nonSerializableState = new Object()

                CredentialsProvider(String profilePath) {
                    commandLine = new File(profilePath).readLines()
                }

                String resolveCredentials() {
                    def standardOutput = new ByteArrayOutputStream()
                    def process = commandLine.execute()
                    process.waitForProcessOutput(standardOutput, System.err)
                    if (process.exitValue() != 0) {
                        throw new IllegalStateException("Credentials process failed")
                    }
                    return standardOutput.toString().trim()
                }
            }

            class UntrackedCredentialsProvider {
                private final CredentialsProvider delegate
                private final ConfigurationCacheInputTracking inputTracking

                UntrackedCredentialsProvider(CredentialsProvider delegate, ConfigurationCacheInputTracking inputTracking) {
                    this.delegate = delegate
                    this.inputTracking = inputTracking
                }

                String resolveCredentials() {
                    return inputTracking.withInputTrackingDisabledUnsafe(delegate::resolveCredentials)
                }
            }

            class CustomBuildCache extends AbstractBuildCache {
                CredentialsProviderFactory credentialsProviderFactory
            }

            class CustomBuildCacheServiceFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                private final ConfigurationCacheInputTracking inputTracking
                private final ProviderFactory providers

                @Inject
                CustomBuildCacheServiceFactory(ConfigurationCacheInputTracking inputTracking, ProviderFactory providers) {
                    this.inputTracking = inputTracking
                    this.providers = providers
                }

                @Override
                BuildCacheService createBuildCacheService(CustomBuildCache configuration, Describer describer) {
                    def credentialsProperty = inputTracking.withInputTrackingDisabledUnsafe(
                        providers.gradleProperty("credentialsProperty")::get
                    )
                    def credentialsProvider = inputTracking.withInputTrackingDisabledUnsafe(
                        configuration.credentialsProviderFactory::create
                    )
                    def untrackedCredentialsProvider = new UntrackedCredentialsProvider(credentialsProvider, inputTracking)
                    println "credentials property during service creation = " + credentialsProperty
                    println "credentials during service creation = " + untrackedCredentialsProvider.resolveCredentials()
                    return new CustomBuildCacheService(untrackedCredentialsProvider)
                }
            }

            class CustomBuildCacheService implements BuildCacheService {
                private final UntrackedCredentialsProvider credentialsProvider

                CustomBuildCacheService(UntrackedCredentialsProvider credentialsProvider) {
                    this.credentialsProvider = credentialsProvider
                }

                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    println "credentials during cache load = " + credentialsProvider.resolveCredentials()
                    return false
                }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                }

                @Override
                void close() throws IOException {
                }
            }

            buildCache {
                registerBuildCacheService(CustomBuildCache, CustomBuildCacheServiceFactory)
                local {
                    enabled = false
                }
                remote(CustomBuildCache) {
                    push = true
                    credentialsProviderFactory = new ProfileCredentialsProviderFactory(
                        new File(settingsDir, "credentials.profile").absolutePath
                    )
                }
            }
        """
        buildFile << """
            plugins {
                id("java")
            }
        """
        file("src/main/java/Main.java") << "class Main {}"

        when:
        executer.withEnvironmentVars(
            CREDENTIALS: "first-credentials",
            ORG_GRADLE_PROJECT_credentialsProperty: "first-property"
        )
        configurationCacheRun("compileJava", "--build-cache")

        then:
        configurationCache.assertStateStored()
        outputContains("credentials property during service creation = first-property")
        outputContains("credentials during service creation = CREDENTIALS=first-credentials")
        outputContains("credentials during cache load = CREDENTIALS=first-credentials")

        when:
        executer.withEnvironmentVars(
            CREDENTIALS: "second-credentials",
            ORG_GRADLE_PROJECT_credentialsProperty: "second-property"
        )
        file("build").deleteDir()
        configurationCacheRun("compileJava", "--build-cache")

        then:
        configurationCache.assertStateLoaded()
        outputContains("credentials property during service creation = second-property")
        outputContains("credentials during service creation = CREDENTIALS=second-credentials")
        outputContains("credentials during cache load = CREDENTIALS=second-credentials")
    }
}
