package org.gradle.build

public class ParaTagHandler extends TagHandler {
    StringWriter contentWriter

    public ParaTagHandler(PrintWriter writer) {
        super(writer)
        startCollecting()
    }

    public TagHandler startEnvironment(String name) {
        if (name == 'itemize') {
            stopCollecting()
            return new ItemizeTagHandler(writer)
        }
        if (name == 'description') {
            stopCollecting()
            return new VariableListTagHandler(writer)
        }
        if (name == 'Verbatim') {
            stopCollecting()
            return new VerbatimTagHandler(writer)
        }
        if (name == 'table') {
            stopCollecting()
            return new TableTagHandler(writer)
        }
        null
    }

    public TagHandler endEnvironment(String name) {
        startCollecting()
        null
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'codeInput') {
            withNoCollection {
                String href = '../../../src/docs/userguide/' + params[0]
                String title = 'Sample ' + params[0].replaceFirst('\\.\\./\\.\\./samples/', '')
                writer.println("<example><title>${title}</title><programlisting><xi:include href='$href' parse='text'/></programlisting></example>")
            }
            return
        }
        if (name == 'codeGradleFile') {
            withNoCollection {
                String href = '../../../src/docs/userguide/' + params[1]
                String title = params[0] + ' build.gradle'
                writer.println("<example><title>${title}</title><programlisting><xi:include href='$href' parse='text'/></programlisting></example>")
            }
            return
        }
        if (name == 'VerbatimInput') {
            withNoCollection {
                String href = '../../../src/docs/userguide/' + params[1]
                String title = params[0].split(',')[1].split('=')[1]
                writer.println("<example><title>${title}</title><programlisting><xi:include href='$href' parse='text'/></programlisting></example>")
            }
            return
        }
        if (name == 'outputInputTutorial') {
            withNoCollection {
                String href = '../../../src/samples/userguideOutput/' + params[0] + '.out'
                String title = "Sample ${params[0]} output"
                writer.println("<example><title>${title}</title><screen><xi:include href='$href' parse='text'/></screen></example>")
            }
            return
        }
        if (name == 'outputInputGradle') {
            withNoCollection {
                String href = '../../../src/docs/userguide/' + params[1]
                String title = params[0]
                writer.println("<example><title>${title}</title><screen><xi:include href='$href' parse='text'/></screen></example>")
            }
            return
        }
        super.directive(name, directive, params)
    }

    def withNoCollection(Closure c) {
        stopCollecting()
        c()
        startCollecting()
    }
    
    def startCollecting() {
        contentWriter = new StringWriter()
        delegateTo new TextTagHandler(new PrintWriter(contentWriter))
    }

    def stopCollecting() {
        delegateTo null
        String content = contentWriter.toString().trim()

        contentWriter = null

        if (!content) {
            return
        }

        writer.print('<para>')
        BufferedReader reader = new BufferedReader(new StringReader(content))
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim()) {
                writer.println('</para>')
                writer.print('<para>')
            }
            else {
                writer.println(line)
            }
        }
        writer.println('</para>')
    }
    
    def end() {
        delegateTo null
        stopCollecting()

        super.end()
    }
}