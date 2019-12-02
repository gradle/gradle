/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import com.google.common.io.CharStreams;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReleaseNotesTransformer implements Transformer<String, String> {
    private final File baseStyle;
    private final File releaseNotesStyle;
    private final File scriptJs;
    private final File jquery;

    public ReleaseNotesTransformer(File baseStyle, File releaseNotesStyle, File scriptJs, File jquery) {
        this.baseStyle = baseStyle;
        this.releaseNotesStyle = releaseNotesStyle;
        this.scriptJs = scriptJs;
        this.jquery = jquery;
    }

    @Override
    public String transform(String original) {
        Document document = Jsoup.parse(original);
        document.outputSettings().indentAmount(2).prettyPrint(true);
        document.prependChild(new DocumentType("html", "", "", ""));
        document.head().
                append("<meta charset='utf-8'>").
                append("<meta name='viewport' content='width=device-width, initial-scale=1'>").
                append("<title>Gradle @version@ Release Notes</title>").
                append("<link rel='stylesheet' type='text/css' href='https://assets.gradle.com/lato/css/lato-font.css'/>");
        addCssToHead(document);
        addJavascriptToHead(document);

        document.body().prepend("<h3 class='releaseinfo'>Version @version@</h3>");
        document.body().prepend("<h1>Gradle Release Notes</h1>");

        wrapH2InSectionTopic(document);
        addAnchorsForHeadings(document);
        addTOC(document);
        wrapContentInContainer(document);

        String rewritten = document.body().html();
        // Turn Gradle Jira issue numbers into issue links
        rewritten = rewritten.replaceAll("GRADLE-\\d+", "<a href=\"https://issues.gradle.org/browse/$0\">$0</a>");
        // Turn Gradle Github issue numbers into issue links
        rewritten = rewritten.replaceAll("(gradle\\/[a-zA-Z\\-_]+)#(\\d+)", "<a href=\"https://github.com/$1/issues/$2\">$0</a>");
        document.body().html(rewritten);

        return document.toString();
    }

    private void addJavascriptToHead(Document document) {
        document.head().append("<script type='text/javascript'>");
        appendFileContentsTo(scriptJs, document.head());
        document.head().append("</script>");
        document.head().append("<script type='text/javascript'>");
        appendFileContentsTo(jquery, document.head());
        document.head().append("</script>");
    }

    private void addCssToHead(Document document) {
        document.head().append("<style>");
        appendFileContentsTo(baseStyle, document.head());
        appendFileContentsTo(releaseNotesStyle, document.head());
        document.head().append("</style>");
    }

    private void appendFileContentsTo(File file, Element element) {
        try (FileReader reader = new FileReader(file)) {
            element.append(CharStreams.toString(reader));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void wrapContentInContainer(Document document) {
        // Wrap the page in a text container to get the margins
        Elements bodyContent = document.body().children().remove();
        document.body().prepend("<div class='container'/>");
        document.body().children().get(0).html(bodyContent.outerHtml());
    }

    private void addTOC(Document document) {
        Element tocSection = document.body().select("section.topic").first().before("<section class='table-of-contents'/>").previousElementSibling();
        tocSection.append("<h2>Table Of Contents</h2>");
        Element toc = tocSection.append("<ul class='toc'/>").children().last();

        for (Element topic : document.body().select(".topic")) {
            Element topicHeading = topic.select("h2").first();
            String name = topicHeading.text();
            String anchor = topicHeading.attr("id");
            toc.append("<li><a/></li>").children().last().select("a").first().text(name).attr("href", "#" + anchor);
        }
    }

    private void addAnchorsForHeadings(Document document) {
        // add anchors for all of the headings
        for (Element heading : document.body().select("h2,h3")) {
            String anchorName = heading.text().toLowerCase().replaceAll(" ", "-");
            heading.attr("id", anchorName);
        }
    }

    private void wrapH2InSectionTopic(Document document) {
        Element heading = document.body().select("h2").first();

        List<Element> inSection = new ArrayList<>();
        inSection.add(heading);

        Element next = heading.nextElementSibling();
        while (true) {
            if (next == null || next.tagName() == "h2") {
                Element section = heading.before("<section class='topic'/>").previousElementSibling();
                Elements inSectionElements = new Elements(inSection);
                section.html(inSectionElements.outerHtml());
                inSectionElements.remove();

                if (next == null) {
                    break;
                } else {
                    inSection.clear();
                    inSection.add(next);
                    heading = next;
                }
            } else {
                inSection.add(next);
            }
            next = next.nextElementSibling();
        }
    }
}
