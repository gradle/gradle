/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TextReportRendererTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final TextReportRenderer<String> renderer = new TextReportRenderer<String>() {
        @Override protected void writeTo(String model, Writer out) {
            out.write("[")
            out.write(model)
            out.write("]")
        }
    }

    def "writes report to output file"() {
        def reportFile = tmpDir.file("dir/report.txt")

        when:
        renderer.writeTo("test", reportFile)

        then:
        reportFile.text == "[test]"
    }
}
