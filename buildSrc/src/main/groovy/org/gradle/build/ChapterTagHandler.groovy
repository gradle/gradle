package org.gradle.build

public class ChapterTagHandler extends StructureTagHandler {
    public ChapterTagHandler(PrintWriter writer) {
        super(writer, 'chapter', 'section', 'xmlns:xi="http://www.w3.org/2001/XInclude"')
    }

    def StructureTagHandler nestedHandler() {
        new SectionTagHandler(writer)
    }
}