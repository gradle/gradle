package org.gradle.kotlin.dsl

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.file.ContentFilterable
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import java.io.FilterReader


class ContentFilterableExtensionsTest {

    @Test
    fun `filter extensions`() {

        val filterable = mock<ContentFilterable> {
            on { filter(any<Class<out FilterReader>>()) } doAnswer { it.mock as ContentFilterable }
            on { filter(any(), any<Class<out FilterReader>>()) } doAnswer { it.mock as ContentFilterable }
        }

        filterable.apply {
            filter<StripJavaComments>()
            filter<HeadFilter>("lines" to 25, "skip" to 1)
            filter<HeadFilter>(mapOf("lines" to 52, "skip" to 2))
            filter(StripJavaComments::class)
            filter(HeadFilter::class, "lines" to 25, "skip" to 3)
            filter(HeadFilter::class, mapOf("lines" to 52, "skip" to 4))
        }

        inOrder(filterable) {
            verify(filterable).filter(StripJavaComments::class.java)
            verify(filterable).filter(mapOf("lines" to 25, "skip" to 1), HeadFilter::class.java)
            verify(filterable).filter(mapOf("lines" to 52, "skip" to 2), HeadFilter::class.java)
            verify(filterable).filter(StripJavaComments::class.java)
            verify(filterable).filter(mapOf("lines" to 25, "skip" to 3), HeadFilter::class.java)
            verify(filterable).filter(mapOf("lines" to 52, "skip" to 4), HeadFilter::class.java)
            verifyNoMoreInteractions()
        }
    }
}
