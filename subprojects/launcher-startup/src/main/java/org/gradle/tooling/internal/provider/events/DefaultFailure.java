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
package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.InternalFailure;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

public class DefaultFailure implements Serializable, InternalFailure {

    private final String message;
    private final String description;
    private final DefaultFailure cause;

    public DefaultFailure(String message, String description, DefaultFailure cause) {
        this.message = message;
        this.description = description;
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public List<? extends InternalFailure> getCauses() {
        return cause == null ? Collections.<InternalFailure>emptyList() : Collections.singletonList(cause);
    }

    public static DefaultFailure fromThrowable(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        DefaultFailure causeFailure = cause != null && cause != t ? fromThrowable(cause) : null;
        return new DefaultFailure(t.getMessage(), out.toString(), causeFailure);
    }

}
