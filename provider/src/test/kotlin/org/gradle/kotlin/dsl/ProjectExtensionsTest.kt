package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Project
import org.gradle.api.plugins.Convention
import org.gradle.api.publish.PublishingExtension

import org.junit.Assert.fail
import org.junit.Test


class ProjectExtensionsTest {

    @Test
    fun `can configure deferred configurable extension`() {

        val extensionType = PublishingExtension::class.java

        val convention = mock<Convention>()
        val project = mock<Project> {
            on { getConvention() } doReturn convention
        }

        project.configure<PublishingExtension> {
            fail("configuration action should be deferred")
        }

        verify(convention).configure(eq(extensionType), any())
    }
}
