package org.gradle.kotlin.dsl

import org.gradle.api.file.ContentFilterable
import org.gradle.test.fixtures.file.HeadTestFilterReader
import org.gradle.test.fixtures.file.UpperCaseTestFilterReader
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
            filter<UpperCaseTestFilterReader>()
            filter<HeadTestFilterReader>("lines" to 25, "skip" to 1)
            filter<HeadTestFilterReader>(mapOf("lines" to 52, "skip" to 2))
            filter(UpperCaseTestFilterReader::class)
            filter(HeadTestFilterReader::class, "lines" to 25, "skip" to 3)
            filter(HeadTestFilterReader::class, mapOf("lines" to 52, "skip" to 4))
        }

        inOrder(filterable) {
            verify(filterable).filter(UpperCaseTestFilterReader::class.java)
            verify(filterable).filter(mapOf("lines" to 25, "skip" to 1), HeadTestFilterReader::class.java)
            verify(filterable).filter(mapOf("lines" to 52, "skip" to 2), HeadTestFilterReader::class.java)
            verify(filterable).filter(UpperCaseTestFilterReader::class.java)
            verify(filterable).filter(mapOf("lines" to 25, "skip" to 3), HeadTestFilterReader::class.java)
            verify(filterable).filter(mapOf("lines" to 52, "skip" to 4), HeadTestFilterReader::class.java)
            verifyNoMoreInteractions()
        }
    }
}
