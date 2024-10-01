/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs;

import com.google.common.io.CharStreams;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deeply opinionated file filter that adds elements to the release notes HTML page.
 */
public class ReleaseNotesTransformer extends FilterReader {
    private File baseCss;
    private File releaseNotesCss;
    private File releaseNotesJavascript;
    private Set<File> jqueryFiles;

    public ReleaseNotesTransformer(Reader original) {
        super(original);
        this.in = new Reader() {
            Reader delegate = null;

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (delegate == null) {
                    delegate = transform(original);
                }
                return delegate.read(cbuf, off, len);
            }

            @Override
            public void close() throws IOException {
                if (delegate != null) {
                    delegate.close();
                }
            }
        };
        this.lock = this.in;
    }

    private Reader transform(Reader in) throws IOException {
        if (jqueryFiles == null || releaseNotesJavascript == null || baseCss == null || releaseNotesCss == null) {
            throw new GradleException("filter isn't ready to transform");
        }

        Document document = Jsoup.parse(CharStreams.toString(in));
        document.outputSettings().indentAmount(2).prettyPrint(true);
        document.prependChild(new DocumentType("html", "", ""));
        document.head().
                append("<meta charset='utf-8'>").
                append("<meta name='viewport' content='width=device-width, initial-scale=1'>").
                append("<title>Gradle @version@ Release Notes</title>").
                append("<link rel='stylesheet' type='text/css' href='https://assets.gradle.com/lato/css/lato-font.css'/>");
        addCssToHead(document);
        addJavascriptToHead(document);

        wrapH2InSectionTopic(document);
        addAnchorsForHeadings(document);
        document.body().prepend("<h1>Gradle Release Notes</h1>");
        addTOC(document);
        wrapContentInContainer(document);

        String rewritten = document.body().html();
        // Turn Gradle Jira issue numbers into issue links
        rewritten = rewritten.replaceAll("GRADLE-\\d+", "<a href=\"https://issues.gradle.org/browse/$0\">$0</a>");
        // Turn Gradle Github issue numbers into issue links
        rewritten = rewritten.replaceAll("(gradle/[a-zA-Z\\-_]+)#(\\d+)", "<a href=\"https://github.com/$1/issues/$2\">$0</a>");
        document.body().html(rewritten);

        return new StringReader(document.toString());
    }

    private void addJavascriptToHead(Document document) {
        for (File jquery : this.jqueryFiles) {
            appendFileContentsTo(document.head(), "<script type='text/javascript'>", jquery, "</script>");
        }
        appendFileContentsTo(document.head(), "<script type='text/javascript'>", releaseNotesJavascript, "</script>");
    }

    private void addCssToHead(Document document) {
        appendFileContentsTo(document.head(), "<style>", baseCss, "</style>");
        appendFileContentsTo(document.head(), "<style>", releaseNotesCss, "</style>");
    }

    private void appendFileContentsTo(Element element, String open, File file, String close) {
        try (FileReader reader = new FileReader(file)) {
            element.append(open + CharStreams.toString(reader) + close);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void wrapH2InSectionTopic(Document document) {
        Element heading = document.body().select("h2").first();

        List<Element> inSection = new ArrayList<>();
        inSection.add(heading);

        Element next = heading.nextElementSibling();
        while (true) {
            if (next == null || next.tagName().equals("h2")) {
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

        Elements h23elements = document.select("h2,h3");
        for (Element h23element: h23elements) {
            String tag = h23element.tagName();
            String name = h23element.text();
            String anchor = h23element.attr("id");
            if(!name.startsWith("Table") && tag.equals("h2")){
                toc.append("<li class=\"mainTopic\"><a/></li>").children().last().select("a").first().text(name).attr("href", "#" + anchor);
            } else if(!name.startsWith("Table") && tag.equals("h3")){
                toc.append("<li class=\"subTopic\"><a/></li>").children().last().select("a").first().text(name).attr("href", "#" + anchor);
            }
        }
    }

    private void addAnchorsForHeadings(Document document) {
        // add anchors for all of the headings
        for (Element heading : document.body().select("h2,h3,h4")) {
            String anchorName = heading.text().toLowerCase(Locale.ROOT).replaceAll(" ", "-");
            heading.attr("id", anchorName);
        }
    }

    public void setJqueryFiles(Set<File> jqueryFiles) {
        this.jqueryFiles = jqueryFiles;
    }

    public void setBaseCss(File baseCss) {
        this.baseCss = baseCss;
    }

    public void setReleaseNotesCss(File releaseNotesCss) {
        this.releaseNotesCss = releaseNotesCss;
    }

    public void setReleaseNotesJavascript(File releaseNotesJavascript) {
        this.releaseNotesJavascript = releaseNotesJavascript;
    }
}
