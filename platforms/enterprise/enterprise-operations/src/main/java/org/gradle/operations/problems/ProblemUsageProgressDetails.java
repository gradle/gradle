/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.operations.problems;

import javax.annotation.Nullable;
import java.util.List;

public interface ProblemUsageProgressDetails {

    ProblemDefinition getProblemDefinition();

    @Nullable
    String getContextualLabel();

    /**
     * Returns solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     */
    List<String> getSolutions();

    /**
     * A long description detailing the problem.
     * <p>
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use {@link #getSolutions()}.
     */
    @Nullable
    String getDetails();

    /**
     * Return the location data associated available for this problem.
     */
    List<ProblemLocation> getLocations();

    /**
     * The failure that caused the problem.
     */
    @Nullable
    Throwable getFailure();

    interface ProblemDefinition {
        String getName();
        String getDisplayName();
        List<ProblemGroup> getGroup();

        String getSeverity();
        @Nullable
        DocumentationLink getDocumentationLink();
    }

    interface ProblemGroup {
        String getId();
        String getDisplayName();
    }

    interface DocumentationLink {
        /**
         * The URL to the documentation page.
         */
        @Nullable
        String getUrl();

        /**
         * A message that tells the user to consult the documentation.
         * There are currently 2 different messages used for this, hence this method.
         */
        @Nullable
        String getConsultDocumentationMessage();
    }

    interface ProblemLocation {
        String getDisplayName();
    }

    interface PluginIdLocation extends ProblemLocation {
        /**
         * The plugin ID.
         *
         * @return the plugin ID
         */
        String getPluginId();
    }

    interface FileLocation extends ProblemLocation {
        // TODO: Add getFileType() - build logic file, software definition, application code
        /**
         * The path to the file.
         *
         * @return the file path
         */
        String getPath();
    }

    interface OffsetInFileLocation extends FileLocation {
        /**
         * The global offset from the beginning of the file.
         *
         * @return the zero-indexed the offset
         */
        int getOffset();

        /**
         * The content of the content starting from {@link #getOffset()}.
         *
         * @return the length
         */
        int getLength();
    }

    interface LineInFileLocation extends FileLocation {
        /**
         * The line number within the file.
         * <p>
         * The line is <b>one-indexed</b>, i.e. the first line in the file is line number 1.
         *
         * @return the line number
         */
        int getLine();

        /**
         * The starting column on the selected line.
         * <p>
         * The column is <b>one-indexed</b>, i.e. the first column in the file is line number 1.
         * A non-positive value indicates that the column information is not available.
         *
         * @return the column
         */
        @Nullable
        Integer getColumn();

        /**
         * The length of the selected content starting from specified column.
         * A negative value indicates that the column information is not available.
         *
         * @return the length
         */
        @Nullable
        Integer getLength();
    }

    interface TaskPathLocation extends ProblemLocation {
        String getBuildPath();
        String getPath();
    }

}
