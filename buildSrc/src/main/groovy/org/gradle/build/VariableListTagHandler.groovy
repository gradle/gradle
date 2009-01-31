package org.gradle.build

public class VariableListTagHandler extends TagHandler {

    public VariableListTagHandler(PrintWriter writer) {
        super(writer)
    }

    def start() {
        writer.println('<variablelist>')
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'item') {
            ParaTagHandler handler = new ParaTagHandler(writer)
            handler.doLast = { writer.println('</listitem></varlistentry>') }
            delegateTo handler
            writer.print("<varlistentry><term>")
            TextTagHandler termHandler = new TextTagHandler(writer)
            new LatexParser().parse(params[0], termHandler)
            termHandler.end()
            writer.print('</term><listitem>')
            return
        }
        super.directive(name, directive, params)
    }

    def end() {
        super.end()
        writer.println('</variablelist>')
    }
}