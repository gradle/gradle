package org.gradle.build.docs.releasenotes

import org.jsoup.nodes.Document

import org.jsoup.Jsoup

class ReleaseNotes {

    final File source
    final File rendered
    String encoding

    ReleaseNotes(File source, File rendered, String encoding) {
        this.source = source
        this.rendered = rendered
        this.encoding = encoding
    }

    Document getRenderedDocument() {
        Jsoup.parse(renderedText)
    }

    String getSourceText() {
        source.getText(encoding)
    }

    String getRenderedText() {
        rendered.getText(encoding)
    }

}
