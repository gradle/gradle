package org.gradle.build

public class TableTagHandler extends BufferedTagHandler {
    String label
    String title
    BufferedTagHandler handler
    int rowCount = 0
    int colCount = 0

    def TableTagHandler(PrintWriter writer) {
        super(writer)
        handler = new BufferedTagHandler()
        TagHandler textHandler = new TextTagHandler(handler.writer)
        textHandler.ignoredDirectives << 'hline'
        textHandler.ignoredDirectives << 'multicolumn'
        textHandler.ignoredEnvironments << 'tabular'
        textHandler.ignoredEnvironments << 'tabularx'
        textHandler.ignoredEnvironments << 'center'
        handler.delegateTo textHandler
    }

    def start() {
    }
    
    def text(String text) {
        String current = text
        while (current) {
            int cellEnd = current.indexOf('&')
            int rowEnd = current.indexOf('\\\\')
            if (cellEnd < 0 && rowEnd < 0) {
                handler.text(current)
                current = null
            }
            else if (cellEnd >= 0 && (rowEnd <0 || cellEnd < rowEnd)) {
                handler.text(current.substring(0, cellEnd))
                writeCell()
                current = current.substring(cellEnd + 1)
            }
            else {
                handler.text(current.substring(0, rowEnd))
                writeEndRow()
                current = current.substring(rowEnd + 2)
            }
        }
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'textbf' && rowCount <= 1) {
            new LatexParser().parse(params[0], this)
            return
        }
        if (name == 'label') {
            label = params[0]
            return
        }
        if (name == 'caption') {
            title = params[0]
            return
        }
        handler.directive(name, directive, params)
    }

    def end() {
        writeEndRow()
        super.end()
    }

    def writeEndRow() {
        writeCell()
        if (colCount > 0) {
            writer.println(rowCount == 1 ? '</tr></thead>' : '</tr>')
            colCount = 0
        }
    }

    def writeCell() {
        String content = handler.flush().trim()
        if (!content) {
            return
        }
        if (colCount == 0) {
            rowCount++
            writer.println(rowCount == 1 ? '<thead><tr>' : '<tr>')
        }
        writer.print('<td>')
        writer.print(content)
        writer.println('</td>')
        colCount++
    }

    def doEnd(String content) {
        if (!title) {
            println "Need a title for table " + this
        }
        writer.print('<table')
        if (label) {
            writer.print(" id='$label'")
        }
        writer.println('>')
        writer.println("<title>$title</title>")
        writer.println(content)
        writer.println('</table>')
    }
}