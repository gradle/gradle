package org.gradle.build

public class TextTagHandler extends TagHandler {

    public TextTagHandler(PrintWriter writer) {
        super(writer)
        ignoredDirectives << 'noindent'
    }

    def text(String text) {
        writer.print(escape(text))
    }

    private String escape(String text) {
        return text.replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'texttt') {
            writer.print('<literal>')
            new LatexParser().parse(params[0], this)
            writer.print('</literal>')
            return
        }
        if (name == 'emph' || name == 'textbf') {
            writer.print('<emphasis>')
            new LatexParser().parse(params[0], this)
            writer.print('</emphasis>')
            return
        }
        if (name == 'ref') {
            writer.print("<xref linkend='${params[0]}'/>")
            return
        }
        if (name == 'footnote') {
            writer.print('<footnote>')
            ParaTagHandler handler = new ParaTagHandler(writer)
            new LatexParser().parse(params[0], handler)
            handler.end()
            writer.print('</footnote>')
            return
        }
        if (name == 'href') {
            String url = params[0]
            url = url.replaceAll('\\\\API\\s+', 'http://www.gradle.org/JAVADOCDIR/org/gradle/api/')
            writer.print("<ulink url='${escape(url)}'>")
            String content = params[1]
            content = content.replaceAll('\\\\PKG\\s+', 'org.gradle.api.')
            new LatexParser().parse(content, this)
            writer.print('</ulink>')
            return
        }
        if (name == 'url') {
            writer.print("<ulink url='${escape(params[0])}'/>")
            return
        }
        super.directive(name, directive, params)
    }
}