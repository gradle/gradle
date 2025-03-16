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

package org.gradle.api.internal.file.copy;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.DuplicateFileCopyingException;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.internal.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DuplicateHandlingCopyActionDecorator implements CopyAction {

    private final static Logger LOGGER = LoggerFactory.getLogger(DuplicateHandlingCopyActionDecorator.class);
    private final CopyAction delegate;
    private final CopySpecInternal spec;
    private final DocumentationRegistry documentationRegistry;

    public DuplicateHandlingCopyActionDecorator(CopyAction delegate, CopySpecInternal spec, DocumentationRegistry documentationRegistry) {
        this.delegate = delegate;
        this.spec = spec;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Map<RelativePath, String> visitedFiles = new HashMap<>();

        return delegate.execute(action -> stream.process((FileCopyDetailsInternal details) -> {
            if (!details.isDirectory()) {
                DuplicatesStrategy strategy = details.getDuplicatesStrategy();
                RelativePath relativePath = details.getRelativePath();
                if (visitedFiles.containsKey(relativePath)) {
                    if (details.isDefaultDuplicatesStrategy()) {
                        failWithIncorrectDuplicatesStrategySetup(relativePath);
                    }
                    if (strategy == DuplicatesStrategy.EXCLUDE) {
                        return;
                    } else if (strategy == DuplicatesStrategy.FAIL) {
                        throw new DuplicateFileCopyingException(String.format("Cannot copy %s to '%s' because %s has already been copied there.", details.getDisplayName(), buildFormattedOutputPath(relativePath), visitedFiles.get(relativePath)));
                    } else if (strategy == DuplicatesStrategy.WARN) {
                        LOGGER.warn("{} will be copied to '{}', overwriting {}, which has already been copied there.", details.getDisplayName(), buildFormattedOutputPath(relativePath), visitedFiles.get(relativePath));
                    }
                } else {
                    visitedFiles.put(relativePath, details.getDisplayName());
                }
            }

            action.processFile(details);
        }));
    }

    private String buildFormattedOutputPath(RelativePath relativePath) {
        return TextUtil.toPlatformLineSeparators(spec.getDestinationDir() == null ? relativePath.getPathString() : new File(spec.getDestinationDir(), relativePath.getPathString()).getPath());
    }

    private void failWithIncorrectDuplicatesStrategySetup(RelativePath relativePath) {
        throw new InvalidUserCodeException(
            "Entry " + relativePath.getPathString() + " is a duplicate but no duplicate handling strategy has been set. " +
            "Please refer to " + documentationRegistry.getDslRefForProperty("org.gradle.api.tasks.Copy", "duplicatesStrategy") + " for details."
        );
    }
}
