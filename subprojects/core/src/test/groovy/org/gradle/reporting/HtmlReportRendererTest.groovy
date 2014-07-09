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

import org.gradle.api.internal.html.SimpleHtmlWriter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class HtmlReportRendererTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final HtmlReportRenderer renderer = new HtmlReportRenderer()

    def "renders HTML to file encoded using UTF-8"() {
        def destDir = tmpDir.file("out")
        def destFile = destDir.file("index.html")
        def reportRenderer = Mock(ReportRenderer)
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.render("test: \u03b1\u03b2", reportRenderer, destDir)

        then:
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportContext context ->
            context.renderPage("index.html", model, pageRenderer)
        }
        1 * pageRenderer.render(_, _) >> { String model, SimpleHtmlWriter htmlWriter ->
            htmlWriter.startElement("pre").characters(model).endElement()
        }

        and:
        destFile.getText("utf-8") == TextUtil.toPlatformLineSeparators('''<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<pre>test: \u03b1\u03b2</pre>
</html>
''')
    }

    def "copies resources into output directory"() {
        def destDir = tmpDir.file("out")
        def reportRenderer = Mock(ReportRenderer)

        when:
        renderer.render("model", reportRenderer, destDir)

        then:
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportContext context ->
            context.requireResource(getClass().getResource("base-style.css"))
        }

        and:
        destDir.file("css/base-style.css").file
    }
}
