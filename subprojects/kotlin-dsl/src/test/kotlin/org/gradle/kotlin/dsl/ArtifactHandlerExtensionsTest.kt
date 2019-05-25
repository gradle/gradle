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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ArtifactHandlerExtensionsTest {

    @Test
    fun `add artifact`() {
        val publishArtifact: PublishArtifact = mock()
        val artifactHandler: ArtifactHandler = mock {
            on { add(any(), any()) } doReturn publishArtifact
        }
        val artifacts = ArtifactHandlerScope.of(artifactHandler)
        val configuration: Configuration = mock {
            on { name } doReturn "configName"
        }
        val firstNotation = "a:b:c"
        val secondNotation = "d:e:f"

        lateinit var firstArtifact: PublishArtifact
        lateinit var secondArtifact: PublishArtifact
        artifacts {
            firstArtifact = configuration(firstNotation)
            secondArtifact = "string-invoke"(secondNotation)
        }

        verify(artifactHandler).add("configName", firstNotation)
        verify(artifactHandler).add("string-invoke", secondNotation)

        assertThat(firstArtifact, sameInstance(publishArtifact))
        assertThat(secondArtifact, sameInstance(publishArtifact))
    }

    @Test
    fun `add artifact with publish artifact configuration block`() {

        val firstConfiguredArtifact: ConfigurablePublishArtifact = mock()
        val secondConfiguredArtifact: ConfigurablePublishArtifact = mock()
        val artifactHandler: ArtifactHandler = mock {
            on { add(any(), any(), any<Action<in ConfigurablePublishArtifact>>()) } doAnswer { invocation ->
                val action = invocation.getArgument<Action<in ConfigurablePublishArtifact>>(2)
                when (invocation.getArgument<String>(0)) {
                    "configName" -> firstConfiguredArtifact.also(action::invoke)
                    "string-invoke" -> secondConfiguredArtifact.also(action::invoke)
                    else -> null
                }
            }
        }
        val artifacts = ArtifactHandlerScope.of(artifactHandler)
        val configuration: Configuration = mock {
            on { name } doReturn "configName"
        }
        val firstNotation = "a:b:c"
        val secondNotation = "d:e:f"

        lateinit var firstArtifact: PublishArtifact
        lateinit var secondArtifact: PublishArtifact
        artifacts {
            firstArtifact = configuration(firstNotation) {
                extension = "zip"
            }
            secondArtifact = "string-invoke"(secondNotation) {
                extension = "zip"
            }
        }

        verify(artifactHandler).add(eq("configName"), eq(firstNotation), any<Action<ConfigurablePublishArtifact>>())
        verify(firstConfiguredArtifact).extension = "zip"
        assertThat(firstArtifact, sameInstance(firstConfiguredArtifact as PublishArtifact))

        verify(artifactHandler).add(eq("string-invoke"), eq(secondNotation), any<Action<ConfigurablePublishArtifact>>())
        verify(secondConfiguredArtifact).extension = "zip"
        assertThat(secondArtifact, sameInstance(secondConfiguredArtifact as PublishArtifact))
    }
}
