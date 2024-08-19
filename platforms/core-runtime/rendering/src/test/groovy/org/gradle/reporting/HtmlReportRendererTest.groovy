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

import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class HtmlReportRendererTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final HtmlReportRenderer renderer = new HtmlReportRenderer()

    def "renders HTML to file encoded with UTF-8"() {
        def destDir = tmpDir.file("out")
        def reportRenderer = Mock(ReportRenderer)
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.render("test: \u03b1\u03b2", reportRenderer, destDir)

        then:
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportBuilder builder ->
            builder.renderHtmlPage("index.html", model, pageRenderer)
        }
        1 * pageRenderer.render(_, _) >> { String model, HtmlPageBuilder<SimpleHtmlWriter> builder ->
            builder.output.startElement("pre").characters(model).endElement()
        }

        and:
        destDir.file("index.html").getText("utf-8") == TextUtil.toPlatformLineSeparators('''<!DOCTYPE html>
<html>
<pre>test: \u03b1\u03b2</pre>
</html>
''')
    }

    def "can use writer to render multi-page HTML report"() {
        def destDir = tmpDir.file("out")
        def reportRenderer = Mock(ReportRenderer)
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.render("test: \u03b1\u03b2", reportRenderer, destDir)

        then:
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportBuilder builder ->
            builder.renderRawHtmlPage("index.html", model, pageRenderer)
            builder.renderRawHtmlPage("child/other.html", "[${model}]" as String, pageRenderer)
        }
        2 * pageRenderer.render(_, _) >> { String model, HtmlPageBuilder<Writer> builder ->
            builder.output.write("<html>" + model + "</html>")
        }

        and:
        destDir.file("index.html").getText("utf-8") == TextUtil.toPlatformLineSeparators("<html>test: \u03b1\u03b2</html>")
        destDir.file("child/other.html").getText("utf-8") == TextUtil.toPlatformLineSeparators("<html>[test: \u03b1\u03b2]</html>")
    }

    def "can use writer to render single page HTML report"() {
        def destFile = tmpDir.file("out")
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.renderRawSinglePage("test: \u03b1\u03b2", pageRenderer, destFile)

        then:
        1 * pageRenderer.render(_, _) >> { String model, HtmlPageBuilder<Writer> builder ->
            builder.output.write("<html>" + model + "</html>")
        }

        and:
        destFile.getText("utf-8") == TextUtil.toPlatformLineSeparators("<html>test: \u03b1\u03b2</html>")
    }

    def "renders single page HTML to file"() {
        def destFile = tmpDir.file("out.html")
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.renderSinglePage("test: \u03b1\u03b2", pageRenderer, destFile)

        then:
        1 * pageRenderer.render(_, _) >> { String model, HtmlPageBuilder<SimpleHtmlWriter> builder ->
            builder.output.startElement("pre").characters(model).endElement()
        }

        and:
        destFile.getText("utf-8") == TextUtil.toPlatformLineSeparators('''<!DOCTYPE html>
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
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportBuilder builder ->
            builder.requireResource(resource("base-style.css"))
            builder.requireResource(resource("script.js"))
            builder.requireResource(resource("thing.png"))
            builder.requireResource(resource("thing.gif"))
        }

        and:
        destDir.file("css/base-style.css").file
        destDir.file("js/script.js").file
        destDir.file("images/thing.png").file
        destDir.file("images/thing.gif").file
    }

    def "copies page resources into output directory"() {
        def destDir = tmpDir.file("out")
        def reportRenderer = Mock(ReportRenderer)
        def pageRenderer = Mock(ReportRenderer)

        when:
        renderer.render("model", reportRenderer, destDir)

        then:
        1 * reportRenderer.render(_, _) >> { String model, HtmlReportBuilder builder ->
            builder.renderHtmlPage("child/page.html", model, pageRenderer)
        }
        1 * pageRenderer.render(_, _) >> { String model, HtmlPageBuilder<SimpleHtmlWriter> builder ->
            def link = builder.requireResource(getClass().getResource("base-style.css"))
            builder.output.startElement("pre").characters(link).endElement()
        }

        and:
        destDir.file("child/page.html").getText("utf-8").contains("<pre>../css/base-style.css</pre>")
        destDir.file("css/base-style.css").file
    }

    def resource(String name) {
        def file = tmpDir.file("tmp", name)
        file.parentFile.mkdirs()
        file.text = "not empty"
        file.toURI().toURL()
    }
}
