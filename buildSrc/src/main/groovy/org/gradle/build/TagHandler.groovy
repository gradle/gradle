package org.gradle.build

public class TagHandler {
    PrintWriter writer
    private TagHandler delegate
    String currentEnv
    Closure doLast
    Set ignoredDirectives = new HashSet()
    Set ignoredEnvironments = new HashSet()

    def TagHandler(writer) {
        this.writer = writer;
    }

    TagHandler getDelegate() {
        delegate
    }
    
    def delegateTo(TagHandler delegate) {
        if (this.delegate) {
            this.delegate.end()
        }
        this.delegate = delegate
    }
    
    def text(String text) {
        if (delegate) {
            delegate.text(text)
            return
        }
        if (text.trim()) {
            println "ignoring text $text " + this
        }
    }

    public TagHandler startEnvironment(String name) {
        return null
    }

    public TagHandler endEnvironment(String name) {
        return null
    }
    
    def directive(String name, String directive, List<String> params) {
        if (name == 'end' && params[0] == currentEnv) {
            delegateTo null
            currentEnv = null
            TagHandler handler = endEnvironment(params[0])
            if (handler) {
                handler.start()
                delegateTo handler
            }
            return
        }
        if (name == 'begin') {
            TagHandler handler = startEnvironment(params[0])
            if (handler) {
                delegateTo null
                handler.start()
                delegateTo(handler)
                currentEnv = params[0]
                return
            }
        }

        if (delegate) {
            delegate.directive(name, directive, params)
            return
        }

        if (name == 'begin' && ignoredEnvironments.contains(params[0])) {
            return
        }
        if (name == 'end' && ignoredEnvironments.contains(params[0])) {
            return
        }
        if (ignoredDirectives.contains(name)) {
            return
        }

        println "ignore $directive $params " + this
    }
    
    def end() {
        if (delegate) {
            delegate.end()
        }
        if (doLast) {
            doLast()
        }
    }
}