/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.groovy.scripts;

import org.gradle.api.GradleScriptException;

/**
 * A {@code ScriptCompilationException} is thrown when a script cannot be compiled.
 */
public class ScriptCompilationException extends GradleScriptException {
    private final ScriptSource scriptSource;
    private final Integer lineNumber;

    public ScriptCompilationException(String message, Throwable cause, ScriptSource scriptSource, Integer lineNumber) {
        super(message, cause);
        this.scriptSource = scriptSource;
        this.lineNumber = lineNumber;
    }

    public ScriptSource getScriptSource() {
        return scriptSource;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }
}
