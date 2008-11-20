package org.gradle.groovy.scripts;

import groovy.lang.Script;

public abstract class ScriptWithSource extends Script {
    private ScriptSource source;

    public ScriptSource getScriptSource() {
        return source;
    }

    public void setSource(ScriptSource source) {
        this.source = source;
    }
}
