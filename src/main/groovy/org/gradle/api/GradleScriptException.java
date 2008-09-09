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
import org.gradle.util.GUtil;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * <p>A <code>GradleScriptException</code> is thrown when an exception occurs in the parsing or execution of a
 * script.</p>
 *
 * @author Hans Dockter
 */
public class GradleScriptException extends GradleException {
    private final String originalMessage;
    private final ScriptSource scriptSource;

    public GradleScriptException(String message, Throwable cause, ScriptSource scriptSource) {
        super(null, cause);
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
        String scriptName = scriptSource.getClassName();
        List<Integer> lineNumbers = new ArrayList<Integer>();
        for (Throwable currentException = this; currentException != null; currentException = currentException.getCause()) {
            for (StackTraceElement element : currentException.getStackTrace()) {
                if (element.getFileName() != null && element.getFileName().equals(scriptName) && element.getLineNumber() >= 0) {
                    lineNumbers.add(element.getLineNumber());
                }
            }
        }
        String lineInfo = !lineNumbers.isEmpty() ? String.format(" line(s): %s", GUtil.join(lineNumbers, ", ")) : "";
        return StringUtils.capitalize(scriptSource.getDescription()) + lineInfo;
    }

    public String getMessage() {
        return String.format("%s%n%s", getLocation(), originalMessage);
    }
}
