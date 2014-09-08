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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.Specification

class TabsRendererTest extends Specification {
    final ReportRenderer<String, SimpleHtmlWriter> contentRenderer = new ReportRenderer<String, SimpleHtmlWriter>() {
        @Override
        void render(String model, SimpleHtmlWriter parent) throws IOException {
        }
    }
    final TabsRenderer renderer = new TabsRenderer()

    def "renders tabs"() {
        def writer = new StringWriter()
        given:
        SimpleHtmlWriter htmlBuilder = new SimpleHtmlWriter(writer);

        and:
        renderer.add('tab 1', contentRenderer)
        renderer.add('tab 2', contentRenderer)

        when:
        renderer.render("test", htmlBuilder)

        def html = html(writer.toString());
        then:
        html.select("div#tabs > ul > li > a").find { it.text() == "tab 1" }
        html.select("div#tabs > ul > li > a").find { it.text() == "tab 2" }

        html.select("div#tabs > div#tab0 > h2").find { it.text() == "tab 1" }
        html.select("div#tabs > div#tab1 > h2").find { it.text() == "tab 2" }
    }

    Document html(String renderedString) {
        Jsoup.parse(renderedString)
    }

}
