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

import org.gradle.util.GUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * <p><code>GradleException</code> is the base class of all exceptions thrown by Gradle.</p>
 *
 * @author Hans Dockter
 */
public class GradleException extends RuntimeException {
    private String scriptName;

    public GradleException() {
    }

    public GradleException(String message) {
        super(message);
    }

    public GradleException(String message, Throwable cause) {
        super(message, cause);
    }

    public GradleException(Throwable cause) {
        super(cause);
    }

    public GradleException(Throwable cause, String scriptName) {
        super(cause);
        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getMessage() {
        if (scriptName == null) {
            return super.getMessage();
        }
        List<Integer> lineNumbers = new ArrayList<Integer>();
        for (Throwable currentException = this; currentException != null; currentException = currentException.getCause()) {
            for (StackTraceElement element : currentException.getStackTrace()) {
                if (element.getFileName() != null && element.getFileName().equals(scriptName) && element.getLineNumber() >= 0) {
                    lineNumbers.add(element.getLineNumber());
                }
            }
        }
        String lineInfo = !lineNumbers.isEmpty()
                ? String.format("in line(s): %s", GUtil.join(lineNumbers, ", "))
                : "No line info available from stacktrace.";
        return String.format("Buildscript=%s %s%s%s", scriptName, lineInfo, System.getProperty("line.separator"),
                super.getMessage());
    }
}