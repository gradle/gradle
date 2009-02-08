package org.gradle.build

public class ChapterTagHandler extends StructureTagHandler {
    public ChapterTagHandler(PrintWriter writer) {
        super(writer, 'chapter', 'section', 'xmlns:xi="http://www.w3.org/2001/XInclude"')
    }

    def doBefore() {
//        writer.println('<!DOCTYPE chapter PUBLIC "-//OASIS//DTD DocBook XML V4.5//EN" "http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd">')
    }
    
    def StructureTagHandler nestedHandler() {
        new SectionTagHandler(writer)
    }
}