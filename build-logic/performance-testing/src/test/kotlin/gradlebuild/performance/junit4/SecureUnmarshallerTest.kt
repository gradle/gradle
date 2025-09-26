/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.performance.junit4

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecureUnmarshallerTest {

    @JvmField
    @Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extracts data from test report`() {
        val result = SecureUnmarshaller().unmarshal(junitReportXml())
        assertEquals(1, result.size)
        val testsuite = result[0]
        assertEquals("com.example.performance.Language", testsuite.name)
        assertEquals("4", testsuite.tests)
        assertEquals("1", testsuite.skipped)
        assertEquals("1", testsuite.failures)
        assertEquals("1", testsuite.errors)
        assertEquals("dev6435.example.com", testsuite.hostname)
    }

    @Test
    fun `extracts success from test report`() {
        val test = SecureUnmarshaller().unmarshal(junitReportXml())[0].testcase[0]
        assertEquals("first run", test.name)
        assertEquals(null, test.failure)
        assertEquals(null, test.skipped)
        assertEquals(null, test.error)
    }

    @Test
    fun `extracts failure from test report`() {
        val test = SecureUnmarshaller().unmarshal(junitReportXml())[0].testcase[1]
        assertEquals("pristine env", test.name)
        assertEquals(1, test.failure.size)
        assertEquals(null, test.skipped)
        assertEquals(null, test.error)
        assertEquals("java.lang.RuntimeException", test.failure[0].type)
        assertEquals("java.lang.RuntimeException: Build failed.", test.failure[0].message)
        assertEquals("java.lang.RuntimeException: Build failed. at...", test.failure[0].content)
    }

    @Test
    fun `extracts skipped from test report`() {
        val test = SecureUnmarshaller().unmarshal(junitReportXml())[0].testcase[2]
        assertEquals("cold weather", test.name)
        assertEquals(null, test.failure)
        assertEquals(1, test.skipped.size)
        assertEquals(null, test.error)
    }

    @Test
    fun `extracts error from test report`() {
        val test = SecureUnmarshaller().unmarshal(junitReportXml())[0].testcase[3]
        assertEquals("bad conditions", test.name)
        assertEquals(null, test.failure)
        assertEquals(null, test.skipped)
        assertEquals(1, test.error.size)
        assertEquals("error", test.error[0].type)
        assertEquals("Error while executing something!", test.error[0].message)
        assertEquals("Honestly, no one knows.", test.error[0].content)
    }

    private
    fun junitReportXml(): File =
        tempFolder.newFile("junit.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.performance.Language" tests="4" skipped="1" failures="1" errors="1" timestamp="2025-05-22T16:11:00.828Z" hostname="dev6435.example.com" time="982.6">
                  <properties/>
                  <testcase name="first run" classname="com.example.performance.Language" time="982.591"/>
                  <testcase name="pristine env" classname="com.example.performance.Language" time="0.004">
                    <failure message="java.lang.RuntimeException: Build failed." type="java.lang.RuntimeException">java.lang.RuntimeException: Build failed. at...</failure>
                  </testcase>
                  <testcase name="cold weather" classname="com.example.performance.Language" time="0.005">
                    <skipped/>
                  </testcase>
                  <testcase name="bad conditions" classname="com.example.performance.Language" time="1.337">
                    <error message="Error while executing something!" type="error">Honestly, no one knows.</error>
                  </testcase>
                  <system-out><![CDATA[
                first run ...
                ]]></system-out>
                  <system-err><![CDATA[]]></system-err>
                </testsuite>
                """.replaceIndent()
            )
        }
}
