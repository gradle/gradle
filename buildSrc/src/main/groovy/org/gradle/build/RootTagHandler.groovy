package org.gradle.build

public class RootTagHandler extends TagHandler {

    def RootTagHandler(writer) {
        super(writer)
    }

    def directive(String name, String directive, List<String> params) {
        if (name == 'chapter') {
            TagHandler handler = new ChapterTagHandler(writer)
            handler.start(params)
            delegateTo handler
            return
        }
        if (name == 'documentclass') {
            TagHandler handler = new DocumentTagHandler(writer)
            handler.start()
            delegateTo handler
            return
        }
        super.directive(name, directive, params)
    }
}