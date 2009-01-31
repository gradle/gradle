package org.gradle.build

public class DocumentTagHandler extends TagHandler {

    public DocumentTagHandler(writer) {
        super(writer)
    }

    def start() {
        writer.println('<book xmlns:xi="http://www.w3.org/2001/XInclude">')
        writer.println('<bookinfo>')
        writer.println('<title>Gradle</title>')
        writer.println('<subtitle>A build system</subtitle>')
        writer.println('<productnumber>0.6</productnumber>')
        writer.println('<copyright><year>2007-2008</year><holder>Hans Dockter</holder></copyright>')
        writer.println('<legalnotice><para>Copies of this document may be made for your own use and for distribution to others, provided that you do not charge any fee for such copies and further provided that each copy contains this Copyright Notice, whether distributed in print or electronically.</para></legalnotice>')
        writer.println('<author><firstname>Hans</firstname><surname>Dockter</surname></author>')
        writer.println('<author><firstname>Adam</firstname><surname>Murdoch</surname></author>')
        writer.println('</bookinfo>')
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'includeonly') {
            params[0].split(',').each {
                writer.println("<xi:include href='${it}.xml'/>")
            }
            return
        }
        super.directive(name, directive, params)
    }

    def end() {
        super.end()
        writer.println('</book>')
    }
}