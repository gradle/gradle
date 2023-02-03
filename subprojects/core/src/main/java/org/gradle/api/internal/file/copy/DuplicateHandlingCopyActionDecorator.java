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
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.WorkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class DuplicateHandlingCopyActionDecorator implements CopyAction {

    private final static Logger LOGGER = LoggerFactory.getLogger(DuplicateHandlingCopyActionDecorator.class);
    private final CopyAction delegate;
    private final DocumentationRegistry documentationRegistry;

    public DuplicateHandlingCopyActionDecorator(CopyAction delegate, DocumentationRegistry documentationRegistry) {
        this.delegate = delegate;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Set<RelativePath> visitedFiles = new HashSet<>();

        return delegate.execute(action -> stream.process(details -> {
            if (!details.isDirectory()) {
                DuplicatesStrategy strategy = details.getDuplicatesStrategy();
                RelativePath relativePath = details.getRelativePath();
                if (!visitedFiles.add(relativePath)) {
                    if (details.isDefaultDuplicatesStrategy()) {
                        failWithIncorrectDuplicatesStrategySetup(relativePath);
                    }
                    if (strategy == DuplicatesStrategy.EXCLUDE) {
                        return;
                    } else if (strategy == DuplicatesStrategy.FAIL) {
                        throw new DuplicateFileCopyingException(String.format("Encountered duplicate path \"%s\" during copy operation configured with DuplicatesStrategy.FAIL", details.getRelativePath()));
                    } else if (strategy == DuplicatesStrategy.WARN) {
                        LOGGER.warn("Encountered duplicate path \"{}\" during copy operation configured with DuplicatesStrategy.WARN", details.getRelativePath());
                    }
                }
            }

            action.processFile(details);
        }));
    }

    private void failWithIncorrectDuplicatesStrategySetup(RelativePath relativePath) {
        throw new InvalidUserCodeException(
            "Entry " + relativePath.getPathString() + " is a duplicate but no duplicate handling strategy has been set. " +
            "Please refer to " + documentationRegistry.getDslRefForProperty(Copy.class, "duplicatesStrategy") + " for details."
        );
    }
}
