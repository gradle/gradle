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

/**
 * <p>A <code>GradleScriptException</code> is thrown when an exception occurs in the parsing or execution of a
 * script.</p>
 *
 * @author Hans Dockter
 */
public class GradleScriptException extends GradleException {
    private final String originalMessage;

    public GradleScriptException(String message, Throwable cause, ScriptSource scriptSource) {
        super(getMessage(message, cause), cause, scriptSource);
        originalMessage = message;
    }

    private static String getMessage(String message, Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return message;
        }
        return String.format("%s%n%s", message, cause.getMessage());
    }

    public String getOriginalMessage() {
        return originalMessage;
    }
}
