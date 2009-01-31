package org.gradle.build

public class ItemizeTagHandler extends TagHandler {
    public ItemizeTagHandler(PrintWriter writer) {
        super(writer)
    }

    def start() {
        writer.println('<itemizedlist>')
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'item') {
            ParaTagHandler handler = new ParaTagHandler(writer)
            handler.doLast = {
                writer.println('</listitem>')
            }
            delegateTo handler
            writer.println('<listitem>')
            return
        }
        super.directive(name, directive, params)
    }

    def end() {
        super.end()
        writer.println('</itemizedlist>')
    }
}