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

import org.apache.commons.lang3.StringUtils;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.Nullable;


/**
 * A {@code LocationAwareException} is an exception which can be annotated with a location in a script.
 */
@UsedByScanPlugin
public class LocationAwareException extends ContextAwareException implements FailureResolutionAware {
    private final String sourcePath;
    private final String sourceDisplayName;
    private final Integer lineNumber;

    public LocationAwareException(Throwable cause, ScriptSource source, Integer lineNumber) {
        this(cause, source != null ? source.getFileName() : null, source != null ? source.getDisplayName() : null, lineNumber);
    }

    public LocationAwareException(Throwable cause, @Nullable String sourcePath, @Nullable String sourceDisplayName, @Nullable Integer lineNumber) {
        super(cause);
        this.sourcePath = sourcePath;
        this.sourceDisplayName = sourceDisplayName;
        this.lineNumber = lineNumber;
    }

    /**
     * Returns the file path of the script where this exception occurred.
     *
     * @return The source file path, or null if not known.
     */
    @Nullable
    public String getSourcePath() {
        return sourcePath;
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
    public String describeLocation() {
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
     * Returns a location string that may be enhanced with a clickable file URI.
     * <p>
     * If the source path contains special characters (whitespace, non-ASCII, etc.) that
     * would prevent IDEs from recognizing it as a clickable link, this method appends
     * a properly encoded file: URI to make the location clickable in IDE consoles.
     * <p>
     * When a line number is available, it is appended to the URI using the {@code :lineNumber}
     * format that IDEs recognize for opening files at specific lines.
     *
     * @return the location string, potentially with a clickable URI appended. Returns the same
     *         as {@link #describeLocation()} if no source path is available.
     * @see #describeLocation()
     * @see BuildExceptionReporter#formatClickableLink(String, Integer)
     */
    @Nullable
    public String describeClickableLocation() {
        if (sourcePath == null) {
            return describeLocation();
        } else {
            return maybeAddClickableLocation(describeLocation());
        }
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
        String location = describeLocation();
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

    /**
     * Enhances the location string with a clickable file URI if needed.
     * <p>
     * IDEs typically tokenize console output by whitespace to detect clickable file paths.
     * When the source path contains characters that break tokenization (whitespace, non-ASCII, etc.),
     * this method appends a properly encoded file: URI on a new line to ensure the location
     * remains clickable in IDE consoles.
     * <p>
     * If URI conversion fails for any reason, the original location is returned to ensure
     * error reporting is not disrupted.
     *
     * @param location the base location string to potentially enhance
     * @return the location string, with a clickable URI appended if the source path needs encoding
     * @see #needsUriEncoding(String)
     * @see BuildExceptionReporter#formatClickableLink(String, Integer)
     */
    private String maybeAddClickableLocation(String location) {
        if (needsUriEncoding(sourcePath)) {
            try {
                return location + "\n  " + BuildExceptionReporter.formatClickableLink(sourcePath, lineNumber);
            } catch (Exception e) {
                // Don't let URI conversion break error reporting
            }
        }
        return location;
    }

    /**
     * Determines if a file path needs URI encoding to be clickable in IDEs.
     * <p>
     * IDEs typically tokenize console output by whitespace to detect clickable file paths.
     * Paths containing certain characters will not be recognized as clickable links and need
     * to be converted to file: URIs with proper percent-encoding.
     * <p>
     * This method detects:
     * <ul>
     * <li>Whitespace characters (space, tab, newline, etc.) - break tokenization</li>
     * <li>Non-ASCII/Unicode characters - may not be recognized by pattern matchers</li>
     * <li>Special characters that could break URI parsing (#, ?, %, etc.)</li>
     * </ul>
     *
     * @param path the file path to check
     * @return true if the path needs URI encoding to be clickable
     */
    private static boolean needsUriEncoding(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);

            // Whitespace breaks IDE tokenization
            if (Character.isWhitespace(c)) {
                return true;
            }

            // Non-ASCII characters may not be recognized by simple pattern matchers
            if (c > '~') {
                return true;
            }

            // Special characters that could break URI parsing or IDE link detection
            // Note: Forward slash (/) and colon (:) are allowed as they're used in paths
            // Note: Backslash (\) is allowed as it's used in Windows paths
            switch (c) {
                case '#':  // Fragment identifier
                case '?':  // Query separator
                case '%':  // Percent encoding
                case '[':  // Square brackets
                case ']':
                case '{':  // Curly braces
                case '}':
                case '|':  // Pipe
                case '^':  // Caret
                case '`':  // Backtick
                case '<':  // Angle brackets (invalid in some file systems)
                case '>':
                case '"':  // Quote (invalid in some file systems)
                    return true;
            }
        }
        return false;
    }
}
