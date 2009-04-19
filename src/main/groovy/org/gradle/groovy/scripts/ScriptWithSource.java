package org.gradle.groovy.scripts;

import groovy.lang.Script;

public abstract class ScriptWithSource extends Script {
    private ScriptSource source;

    public ScriptSource getScriptSource() {
        return source;
    }

    public void setScriptSource(ScriptSource source) {
        this.source = source;
    }
}
