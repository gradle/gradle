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
import org.gradle.api.GradleException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.util.TreeVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code LocationAwareException} is an exception which can be annotated with a location in a script.
 */
@UsedByScanPlugin
public class LocationAwareException extends GradleException implements FailureResolutionAware {
    private final String sourceDisplayName;
    private final Integer lineNumber;

    public LocationAwareException(Throwable cause, ScriptSource source, Integer lineNumber) {
        this(cause, source != null ? source.getDisplayName() : null, lineNumber);
    }

    public LocationAwareException(Throwable cause, String sourceDisplayName, Integer lineNumber) {
        this.sourceDisplayName = sourceDisplayName;
        this.lineNumber = lineNumber;
        initCause(cause);
    }

    /**
     * <p>Returns the display name of the script where this exception occurred.</p>
     *
     * @return The source display name. May return null.
     */
    public String getSourceDisplayName() {
        return sourceDisplayName;
    }

    /**
     * <p>Returns a description of the location of where this exception occurred.</p>
     *
     * @return The location description. May return null.
     */
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
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the fully formatted error message, including the location.
     *
     * @return the message. May return null.
     */
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
    public void appendResolution(StyledTextOutput output, BuildClientMetaData clientMetaData) {
        if (getCause() instanceof FailureResolutionAware) {
            FailureResolutionAware resolutionAware = (FailureResolutionAware) getCause();
            resolutionAware.appendResolution(output, clientMetaData);
        }
    }

    /**
     * Returns the reportable causes for this failure.
     *
     * @return The causes. Never returns null, returns an empty list if this exception has no reportable causes.
     */
    public List<Throwable> getReportableCauses() {
        final List<Throwable> causes = new ArrayList<Throwable>();
        visitCauses(getCause(), new TreeVisitor<Throwable>() {
            @Override
            public void node(Throwable node) {
                causes.add(node);
            }
        });
        return causes;
    }

    /**
     * Visits the reportable causes for this failure.
     */
    public void visitReportableCauses(TreeVisitor<? super Throwable> visitor) {
        visitor.node(this);
        if (this.getCause() != null) {
            visitCauses(this.getCause(), visitor);
        }
    }

    private void visitCauses(Throwable t, TreeVisitor<? super Throwable> visitor) {
        if (t instanceof MultiCauseException) {
            MultiCauseException multiCauseException = (MultiCauseException) t;
            List<? extends Throwable> causes = multiCauseException.getCauses();
            if (!causes.isEmpty()) {
                visitor.startChildren();
                for (Throwable cause : causes) {
                    visitContextual(cause, visitor);
                }
                visitor.endChildren();
            }
        } else if (t.getCause() != null) {
            visitor.startChildren();
            visitContextual(t.getCause(), visitor);
            visitor.endChildren();
        }
    }

    private void visitContextual(Throwable t, TreeVisitor<? super Throwable> visitor) {
        Throwable next = findNearestContextual(t);
        if (next != null) {
            // Show any contextual cause recursively
            visitor.node(next);
            visitCauses(next, visitor);
        } else {
            // Show the direct cause of the last contextual cause only
            visitor.node(t);
        }
    }

    @Nullable
    private Throwable findNearestContextual(@Nullable Throwable t) {
        if (t == null) {
            return null;
        }
        if (t.getClass().getAnnotation(Contextual.class) != null || t instanceof MultiCauseException) {
            return t;
        }
        return findNearestContextual(t.getCause());
    }
}
