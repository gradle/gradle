package org.gradle.build

public class VerbatimTagHandler extends TagHandler {

    def VerbatimTagHandler(PrintWriter writer) {
        super(writer)
    }

    def start() {
        writer.print('<programlisting><![CDATA[')
    }

    def end() {
        writer.println(']]></programlisting>');
        super.end()
    }

    public text(String text) {
        writer.print(text)
    }
}