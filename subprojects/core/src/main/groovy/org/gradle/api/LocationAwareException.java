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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.Contextual;
import org.gradle.api.internal.MultiCauseException;
import org.gradle.groovy.scripts.ScriptSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@code LocationAwareException} is an exception which can be annotated with a location in a script.
 */
public class LocationAwareException extends GradleException {
    private final Throwable target;
    private final ScriptSource source;
    private final Integer lineNumber;

    public LocationAwareException(Throwable cause, Throwable target, ScriptSource source, Integer lineNumber) {
        this.source = source;
        this.lineNumber = lineNumber;
        this.target = target;
        initCause(cause);
    }

    /**
     * Returns the target exception.
     *
     * @return The target exception. Not null
     */
    public Throwable getTarget() {
        return target;
    }

    /**
     * <p>Returns the undecorated message of this exception.</p>
     *
     * @return The undecorated message. May return null.
     */
    public String getOriginalMessage() {
        return target.getMessage();
    }

    /**
     * <p>Returns the source of the script where this exception occurred.</p>
     *
     * @return The source. May return null.
     */
    public ScriptSource getScriptSource() {
        return source;
    }

    /**
     * <p>Returns a description of the location of where this exception occurred.</p>
     *
     * @return The location description. May return null.
     */
    public String getLocation() {
        if (source == null) {
            return null;
        }
        String sourceMsg = StringUtils.capitalize(source.getDisplayName());
        if (lineNumber == null) {
            return sourceMsg;
        }
        return String.format("%s line: %d", sourceMsg, lineNumber);
    }

    /**
     * Returns the line in the script where this exception occurred, if known.
     *
     * @return The line number, or null if not known.
     */
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the fully formatted error message, including the location.
     *
     * @return the message. May return null.
     */
    public String getMessage() {
        String location = getLocation();
        String message = target.getMessage();
        if (location == null && message == null) {
            return null;
        }
        if (location == null) {
            return message;
        }
        if (message == null) {
            return location;
        }
        return String.format("%s%n%s", location, message);
    }

    /**
     * Returns the reportable causes for this failure.
     *
     * @return The causes. Never returns null, returns an empty list if this exception has no reportable causes.
     */
    public List<Throwable> getReportableCauses() {
        List<Throwable> causes = new ArrayList<Throwable>();
        addCauses(target, causes);
        return causes;
    }

    private void addCauses(Throwable t, Collection<Throwable> dest) {
        if (t instanceof MultiCauseException) {
            MultiCauseException multiCauseException = (MultiCauseException) t;
            List<? extends Throwable> causes = multiCauseException.getCauses();
            for (Throwable cause : causes) {
                dest.add(cause);
                if (cause.getClass().getAnnotation(Contextual.class) !=null) {
                    addCauses(cause, dest);
                }
            }
        } else if (t.getCause() != null) {
            Throwable cause = t.getCause();
            dest.add(cause);
            if (cause.getClass().getAnnotation(Contextual.class) != null) {
                addCauses(cause, dest);
            }
        }
    }
}
