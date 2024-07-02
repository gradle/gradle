/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.kotlin.dsl.resolver

import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SourceDistributionResolverTest {
    private fun distributionRepositoryConfig(gradleVersion: String, vararg gradleProperties: Pair<String, String>) =
        SourceDistributionResolver.distributionRepositoryConfig(gradleVersion, FixedGradlePropertiesProviderFactory(gradleProperties.toMap()))

    @Test
    fun `check default values repository config`() {
        val config = distributionRepositoryConfig("8.9")
        assertThat(config.url, equalTo("${SourceDistributionResolver.SERVICES_BASE_URL}/${SourceDistributionResolver.RELEASES_REPOSITORY_NAME}"))
        assertThat(config.credentials, nullValue())
        assertThat(config.repoName, equalTo(SourceDistributionResolver.RELEASES_REPOSITORY_NAME))
    }

    @Test
    fun `check default values repository config with snapshot version`() {
        val config = distributionRepositoryConfig("8.9-20240702011213+0000")
        assertThat(config.url, equalTo("${SourceDistributionResolver.SERVICES_BASE_URL}/${SourceDistributionResolver.SNAPSHOTS_REPOSITORY_NAME}"))
        assertThat(config.credentials, nullValue())
        assertThat(config.repoName, equalTo(SourceDistributionResolver.SNAPSHOTS_REPOSITORY_NAME))
    }

    @Test
    fun `check only credentials override repository config`() {
        val config = distributionRepositoryConfig("8.9",
            SourceDistributionResolver.DISTRIBUTIONS_REPO_USER_OVERRIDE_KEY to "test-user",
            SourceDistributionResolver.DISTRIBUTIONS_REPO_PASSWORD_OVERRIDE_KEY to "test-password"
        )
        assertThat(config.url, equalTo("${SourceDistributionResolver.SERVICES_BASE_URL}/${SourceDistributionResolver.RELEASES_REPOSITORY_NAME}"))
        assertThat(config.credentials, nullValue())
        assertThat(config.repoName, equalTo(SourceDistributionResolver.RELEASES_REPOSITORY_NAME))
    }

    @Test
    fun `check URL only override repository config`() {
        val urlOverride = "https://test.com/distributions-override"
        val config = distributionRepositoryConfig("8.9",
            SourceDistributionResolver.DISTRIBUTIONS_REPO_URL_OVERRIDE_KEY to urlOverride
        )
        assertThat(config.url, equalTo(urlOverride))
        assertThat(config.credentials, nullValue())
        assertThat(config.repoName, equalTo(SourceDistributionResolver.RELEASES_REPOSITORY_NAME))
    }

    @Test
    fun `check URL only override repository config with snapshot version`() {
        val urlOverride = "https://test.com/distributions-override"
        val config = distributionRepositoryConfig("8.9-20240702011213+0000",
            SourceDistributionResolver.DISTRIBUTIONS_REPO_URL_OVERRIDE_KEY to urlOverride
        )
        assertThat(config.url, equalTo(urlOverride))
        assertThat(config.credentials, nullValue())
        assertThat(config.repoName, equalTo(SourceDistributionResolver.SNAPSHOTS_REPOSITORY_NAME))
    }

    @Test
    fun `check override repository config with credentials`() {
        val urlOverride = "https://test.com/distributions-override"
        val testUser = "test-user"
        val testPassword = "test-password"
        val config = distributionRepositoryConfig("8.9",
            SourceDistributionResolver.DISTRIBUTIONS_REPO_URL_OVERRIDE_KEY to urlOverride,
            SourceDistributionResolver.DISTRIBUTIONS_REPO_USER_OVERRIDE_KEY to testUser,
            SourceDistributionResolver.DISTRIBUTIONS_REPO_PASSWORD_OVERRIDE_KEY to testPassword
        )
        assertThat(config.url, equalTo(urlOverride))
        assertThat(config.credentials, notNullValue())
        assertThat(config.credentials!!.username, equalTo(testUser))
        assertThat(config.credentials.password, equalTo(testPassword))
        assertThat(config.repoName, equalTo(SourceDistributionResolver.RELEASES_REPOSITORY_NAME))
    }

    class FixedGradlePropertiesProviderFactory(
        private val gradleProperties: Map<String, String>
    ) : DefaultProviderFactory() {
        override fun gradleProperty(propertyName: Provider<String>): Provider<String> = gradleProperty(propertyName.get())

        override fun gradleProperty(propertyName: String): Provider<String> = Providers.ofNullable(gradleProperties[propertyName])
    }
}
