/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api;

import org.gradle.groovy.scripts.ScriptSource;
import org.apache.commons.lang.StringUtils;

/**
 * <p>A <code>GradleScriptException</code> is thrown when an exception occurs in the parsing or execution of a
 * script.</p>
 *
 * @author Hans Dockter
 */
public class GradleScriptException extends GradleException {
    private final String originalMessage;
    private final ScriptSource scriptSource;
    private final Integer lineNumber;

    public GradleScriptException(String message, Throwable cause, ScriptSource scriptSource) {
        this(message, cause, scriptSource, null);
    }

    public GradleScriptException(String message, Throwable cause, ScriptSource scriptSource, Integer lineNumber) {
        super(cause);
        this.lineNumber = lineNumber;
        assert message != null && cause != null && scriptSource != null;
        originalMessage = message;
        this.scriptSource = scriptSource;
    }

    /**
     * <p>Returns the undecorated message of this exception.</p>
     *
     * @return The undecorated message. Never returns null.
     */
    public String getOriginalMessage() {
        return originalMessage;
    }

    /**
     * <p>Returns the source the script where this of this exception occurred.</p>
     *
     * @return The source. Never returns null.
     */
    public ScriptSource getScriptSource() {
        return scriptSource;
    }

    /**
     * <p>Returns a description of the location of where this execption occurred.</p>
     *
     * @return The location description. Never returns null.
     */
    public String getLocation() {
        Integer lineNumber = getLineNumber();
        String lineInfo = lineNumber != null ? String.format(" line: %s", lineNumber) : "";
        return StringUtils.capitalize(scriptSource.getDisplayName()) + lineInfo;
    }

    /**
     * Returns the line in the script where this exception occurred, if known.
     *
     * @return The line number, or null if not known.
     */
    public Integer getLineNumber() {
        String scriptName = scriptSource.getClassName();
        Integer lineNumber = this.lineNumber;
        if (lineNumber == null) {
            for (Throwable currentException = this; currentException != null; currentException = currentException.getCause()) {
                for (StackTraceElement element : currentException.getStackTrace()) {
                    if (element.getFileName() != null && element.getFileName().equals(scriptName) && element.getLineNumber() >= 0) {
                        lineNumber = element.getLineNumber();
                        break;
                    }
                }
            }
        }
        return lineNumber;
    }

    public String getMessage() {
        return String.format("%s%n%s", getLocation(), originalMessage);
    }

    /**
     * Returns the reportable exception for this failure. Usually, a {code GradleScriptException} is simply a wrapper
     * around the actual failure. This method locates the actual failure in the cause chain of this exception. May
     * return this exception.
     *
     * @return The reportable exception. Never returns null.
     */
    public GradleScriptException getReportableException() {
        GradleScriptException reportable = this;
        for (Throwable t = getCause(); t != null; t = t.getCause()) {
            if (t instanceof GradleScriptException) {
                reportable = (GradleScriptException) t;
            }
        }
        return reportable;
    }
}
