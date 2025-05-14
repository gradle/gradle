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
package org.gradle.internal.exceptions;

import org.apache.commons.lang.StringUtils;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.Nullable;

/**
 * A {@code LocationAwareException} is an exception which can be annotated with a location in a script.
 */
@UsedByScanPlugin
public class LocationAwareException extends ContextAwareException implements FailureResolutionAware {
    private final String sourceDisplayName;
    private final Integer lineNumber;

    public LocationAwareException(Throwable cause, ScriptSource source, Integer lineNumber) {
        this(cause, source != null ? source.getDisplayName() : null, lineNumber);
    }

    public LocationAwareException(Throwable cause, @Nullable String sourceDisplayName, @Nullable Integer lineNumber) {
        super(cause);
        this.sourceDisplayName = sourceDisplayName;
        this.lineNumber = lineNumber;
    }

    /**
     * <p>Returns the display name of the script where this exception occurred.</p>
     *
     * @return The source display name.
     */
    @Nullable
    public String getSourceDisplayName() {
        return sourceDisplayName;
    }

    /**
     * <p>Returns a description of the location of where this exception occurred.</p>
     *
     * @return The location description.
     */
    @Nullable
    public String getLocation() {
        if (sourceDisplayName == null) {
            return null;
        }
        String sourceMsg = StringUtils.capitalize(sourceDisplayName);
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
    @Nullable
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the fully formatted error message, including the location.
     *
     * @return the message. May return null.
     */
    @Nullable
    @Override
    public String getMessage() {
        String location = getLocation();
        String message = getCause().getMessage();
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

    @Override
    public void appendResolutions(Context context) {
        if (getCause() instanceof FailureResolutionAware) {
            FailureResolutionAware resolutionAware = (FailureResolutionAware) getCause();
            resolutionAware.appendResolutions(context);
        }
    }

    @Override
    public void accept(ExceptionContextVisitor contextVisitor) {
        super.accept(contextVisitor);
        String location = getLocation();
        if (location != null) {
            contextVisitor.visitLocation(location);
        }
    }
}
