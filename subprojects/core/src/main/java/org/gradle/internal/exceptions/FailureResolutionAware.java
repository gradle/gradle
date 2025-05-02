/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.function.Consumer;

/**
 * Enhancement interface that exceptions can implement to provide additional information on how to resolve the failure.
 */
public interface FailureResolutionAware {

    void appendResolutions(Context context);

    interface Context {
        BuildClientMetaData getClientMetaData();

        /**
         * Indicates that the build definition is missing.
         */
        void doNotSuggestResolutionsThatRequireBuildDefinition();

        void appendResolution(Consumer<StyledTextOutput> resolution);

        /**
         * Adds a resolution pointing to the user guide. The output matches the following pattern:
         * <code>
         *     ${prefix} http://docs.gradle.org/${currentGradleVersion}/userguide/${userGuideId}.html#${userGuideSection}.
         * </code>
         * @param prefix The string prepended to the documentation URL.
         * @param userGuideId The user guide chapter.
         * @param userGuideSection The user guide section.
         */
        void appendDocumentationResolution(String prefix, String userGuideId, String userGuideSection);
    }
}
