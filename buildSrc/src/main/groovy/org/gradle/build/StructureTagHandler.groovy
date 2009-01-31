package org.gradle.build

public class StructureTagHandler extends BufferedTagHandler {
    private String label
    private String title
    private String tag
    private String ns
    private String nestedType

    public StructureTagHandler(PrintWriter writer, String tag, String nestedType, String ns = null) {
        super(writer)
        this.tag = tag
        this.ns = ns
        this.nestedType = nestedType

        delegateTo new ParaTagHandler(this.writer)
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'label' && !label) {
            label = params[0]
            return
        }
        if (name == nestedType) {
            def currentSection = nestedHandler()
            currentSection.start(params)
            delegateTo currentSection
            return
        }
        super.directive(name, directive, params)
    }

    def StructureTagHandler nestedHandler() {
        throw new UnsupportedOperationException()
    }
    
    def start(List params) {
        title = params[0]
    }

    def doEnd(String content) {
        writer.print("<${tag}")
        if (label) {
            writer.print(" id='$label'")
        }
        if (ns) {
            writer.print(" $ns")
        }
        writer.println(">")
        writer.println("<title>${title}</title>")
        writer.print(content)
        writer.println("</${tag}>")
    }
}