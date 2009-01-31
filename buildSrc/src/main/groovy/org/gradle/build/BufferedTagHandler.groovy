package org.gradle.build

public class BufferedTagHandler extends TagHandler {
    private PrintWriter original
    private Writer content
    private String label
    private String title
    private String tag

    public BufferedTagHandler(PrintWriter writer = null) {
        super(writer)

        if (writer) {
            original = writer
        } else {
            original = new PrintWriter(new StringWriter())
        }

        this.tag = tag

        content = new StringWriter()
        this.writer = new PrintWriter(content)
    }

    def end() {
        delegateTo null
        writer.close()
        writer = original
        doEnd(content.toString())
        super.end()
    }

    def doEnd(String content) {
        writer.print(content)
    }

    def String flush() {
        writer.close()
        String collected = content.toString()
        content = new StringWriter()
        writer = new PrintWriter(content)
        if (delegate) {
            delegate.writer = writer
        }
        collected
    }
}