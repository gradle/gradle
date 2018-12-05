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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.hamcrest.CoreMatchers.equalTo
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
        val notation = "a:b:c"

        lateinit var returnedArtifact: PublishArtifact
        artifacts {
            returnedArtifact = configuration(notation)
        }

        verify(artifactHandler).add("configName", notation)

        assertThat(returnedArtifact, sameInstance(publishArtifact))
    }

    @Test
    fun `add artifact with publish artifact configuration block`() {
        val publishArtifact: PublishArtifact = mock()
        val configurablePublishArtifact: ConfigurablePublishArtifact = mock()
        val artifactHandler: ArtifactHandler = mock {
            on { add(any(), any(), any<Action<in ConfigurablePublishArtifact>>()) } doReturn publishArtifact
        }
        val artifacts = ArtifactHandlerScope.of(artifactHandler)
        val configuration: Configuration = mock {
            on { name } doReturn "configName"
        }
        val notation = "a:b:c"

        lateinit var returnedArtifact: PublishArtifact
        artifacts {
            returnedArtifact = configuration(notation) {
                configurablePublishArtifact.extension = "zip"
            }
        }

        argumentCaptor<Action<in ConfigurablePublishArtifact>>().apply {
            verify(artifactHandler).add(
                eq("configName"),
                eq(notation),
                capture()
            )
            assertThat(allValues.size, equalTo(1))
            firstValue(configurablePublishArtifact)
        }
        verify(configurablePublishArtifact).extension = "zip"
        assertThat(returnedArtifact, sameInstance(publishArtifact))
    }
}
