package org.gradle.build

public class SectionTagHandler extends StructureTagHandler {
    public SectionTagHandler(PrintWriter writer) {
        super(writer, 'section', 'subsection')
    }

    def StructureTagHandler nestedHandler() {
        new SubSectionTagHandler(writer);
    }
}

public class SubSectionTagHandler extends StructureTagHandler {
    public SubSectionTagHandler(PrintWriter writer) {
        super(writer, 'section', 'subsubsection')
    }

    def StructureTagHandler nestedHandler() {
        new StructureTagHandler(writer, 'section', 'no sub element');
    }
}