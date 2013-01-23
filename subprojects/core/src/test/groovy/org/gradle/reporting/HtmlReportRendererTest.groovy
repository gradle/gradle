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
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class HtmlReportRendererTest extends Specification {
    AbstractHtmlReportRenderer abstractHtmlReportRenderer = new AbstractHtmlReportRenderer<String>() {
        @Override
        void render(String model, SimpleHtmlWriter htmlWriter) {
            htmlWriter.startElement("pre").characters(model).endElement()
        }
    }
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final HtmlReportRenderer renderer = new HtmlReportRenderer()

    def "renders report to stream"() {
        StringWriter writer = new StringWriter()
        when:
        renderer.renderer(abstractHtmlReportRenderer).writeTo("test", writer)

        then:
        writer.toString() == TextUtil.toPlatformLineSeparators('''<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<pre>test</pre>
</html>
''')
    }

    def "copies resources into output directory"() {
        File destFile = tmpDir.file('report.txt')

        given:
        renderer.requireResource(getClass().getResource("base-style.css"))

        when:
        renderer.renderer(abstractHtmlReportRenderer).writeTo("test", destFile)

        then:
        tmpDir.file("base-style.css").file
    }
}
