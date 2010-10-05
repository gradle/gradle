/*
 * Copyright 2010 the original author or authors.
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

import java.util.List;

/**
 * A {@code LocationAwareException} is an exception which can be annotated with a location in a script. Note that
 * most implementations of this interface are generated dynamically by an {@link org.gradle.api.internal.ExceptionAnalyser}.
 */
public interface LocationAwareException {
    /**
     * <p>Returns the undecorated message of this exception.</p>
     *
     * @return The undecorated message. May return null.
     */
    public String getOriginalMessage();

    /**
     * <p>Returns the source of the script where this exception occurred.</p>
     *
     * @return The source. May return null.
     */
    public ScriptSource getScriptSource();

    /**
     * <p>Returns a description of the location of where this exception occurred.</p>
     *
     * @return The location description. May return null.
     */
    public String getLocation();

    /**
     * Returns the line in the script where this exception occurred, if known.
     *
     * @return The line number, or null if not known.
     */
    public Integer getLineNumber();

    /**
     * Returns the fully formatted error message, including the location.
     *
     * @return the message. May return null.
     */
    public String getMessage();

    /**
     * Returns the reportable causes for this failure.
     *
     * @return The causes. Never returns null, returns an empty list if this exception has no reportable causes.
     */
    public List<Throwable> getReportableCauses();
}
