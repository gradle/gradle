/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.tooling.internal.protocol.FailureVersion1;

import java.io.PrintWriter;
import java.io.StringWriter;

public class InternalFailure implements FailureVersion1 {
    private final String message;
    private final String description;
    private final InternalFailure cause;

    public InternalFailure(String message, String description, InternalFailure cause) {
        this.cause = cause;
        this.message = message;
        this.description = description;
    }

    public InternalFailure(String message, String description) {
        this.message = message;
        this.description = description;
        this.cause = null;
    }

    /**
     * Returns a short message (typically one line) for the failure.
     * @return the failure message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns a long description of the failure. For example, a stack trace.
     * @return a long description of the failure
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the underlying cause for this failure, if any.
     * @return the cause for this failure, or <i>null</i> if there's no underlying failure or the cause is unknown.
     */
    public InternalFailure getCause() {
        return cause;
    }

    public static InternalFailure fromThrowable(Throwable e) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        e.printStackTrace(wrt);
        Throwable cause = e.getCause();
        InternalFailure causeFailure = cause!=null && cause!=e ? fromThrowable(cause) : null;
        return new InternalFailure(e.getMessage(), out.toString(), causeFailure);
    }

}
