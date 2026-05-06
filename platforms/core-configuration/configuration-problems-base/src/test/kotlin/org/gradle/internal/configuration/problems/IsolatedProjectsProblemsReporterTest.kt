/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.configuration.problems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread


class IsolatedProjectsProblemsReporterTest {

    private val recorded = mutableListOf<PropertyProblem>()

    private val reporter = IsolatedProjectsProblemsReporter(StubProblemFactory, recordingListener(recorded))

    @Test
    fun `report forwards the built problem to the listener`() {
        val problem = newProblem("boom")

        reporter.report { problem }

        assertEquals(1, recorded.size)
        assertSame(problem, recorded.single())
    }

    @Test
    fun `report is suppressed inside an ignoring scope and the problem construction is avoided`() {
        var problemBuilt = false
        reporter.runIgnoringProblemsOnCurrentThread {
            reporter.report {
                problemBuilt = true
                newProblem("ignored")
            }
        }

        assertEquals(emptyList<PropertyProblem>(), recorded)
        assertFalse("problem should not be constructed when ignoring", problemBuilt)
    }

    @Test
    fun `report works again after the ignoring scope exits`() {
        reporter.runIgnoringProblemsOnCurrentThread {
            reporter.report { newProblem("ignored") }
        }
        val after = newProblem("after")
        reporter.report { after }

        assertEquals(listOf(after), recorded)
    }

    @Test
    fun `nested ignoring scopes still suppress until the outermost exits`() {
        reporter.runIgnoringProblemsOnCurrentThread {
            reporter.runIgnoringProblemsOnCurrentThread {
                reporter.report { newProblem("inner") }
            }
            // still inside outer scope
            reporter.report { newProblem("outer") }
        }
        val after = newProblem("after")
        reporter.report { after }

        assertEquals(listOf(after), recorded)
    }

    @Test
    fun `report works again after an ignoring scope exits via exception`() {
        assertThrows(RuntimeException::class.java) {
            reporter.runIgnoringProblemsOnCurrentThread {
                throw RuntimeException("boom")
            }
        }

        val after = newProblem("after")
        reporter.report { after }

        assertEquals(listOf(after), recorded)
    }

    @Test
    fun `ignoring on one thread does not affect other threads`() {
        val threadCount = 3
        val latch = CountDownLatch(threadCount)
        val results = Collections.synchronizedList(mutableListOf<PropertyProblem>())
        val sharedReporter = IsolatedProjectsProblemsReporter(StubProblemFactory, recordingListener(results))

        val ignoringThread = thread {
            sharedReporter.runIgnoringProblemsOnCurrentThread {
                latch.countDown()
                latch.await()
                sharedReporter.report { newProblem("ignored") }
            }
        }
        val reportingThreads = (1..2).map {
            thread {
                latch.countDown()
                latch.await()
                sharedReporter.report { newProblem("from-thread-$it") }
            }
        }

        (reportingThreads + ignoringThread).forEach(Thread::join)

        assertEquals(2, results.size)
    }

    private fun recordingListener(into: MutableList<PropertyProblem>): IsolatedProjectsProblemsListener =
        object : IsolatedProjectsProblemsListener {
            override fun onIsolatedProjectsProblem(problem: PropertyProblem) {
                into += problem
            }
        }

    private fun newProblem(message: String): PropertyProblem =
        PropertyProblem(
            PropertyTrace.Unknown,
            StructuredMessage.Builder().text(message).build()
        )

    private object StubProblemFactory : ProblemFactory {
        override fun locationForCaller(consumer: String?): PropertyTrace = PropertyTrace.Unknown

        override fun problem(
            message: StructuredMessage,
            exception: Throwable?,
            documentationSection: DocumentationSection?,
            getStackTrace: Boolean
        ): PropertyProblem = PropertyProblem(PropertyTrace.Unknown, message, exception, null, documentationSection)

        override fun problem(consumer: String?, messageBuilder: StructuredMessage.Builder.() -> Unit): ProblemFactory.Builder =
            throw UnsupportedOperationException("not used in tests")
    }
}
