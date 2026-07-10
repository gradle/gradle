/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.verification.exceptions;

import org.gradle.api.GradleException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;

/**
 * Exception class used when a GPG IDs were not correct.
 *
 * <p>
 * An example is using short/long IDs instead of fingerprints when trusting keys
 */
public class InvalidGpgKeyIdsException extends GradleException {
    private final List<String> wrongKeys;

    /**
     * Creates a new exception with a list of incorrect keys.
     *
     * @param wrongKeys the list of incorrect IDs, which will be nicely formatted as part of the exception messages so the user can find them
     */
    public InvalidGpgKeyIdsException(List<String> wrongKeys) {
        this.wrongKeys = wrongKeys;
    }

    /**
     * Formats a nice error message by using a {@link TreeFormatter}.
     *
     * <p>
     * Idea for this method is that you can pass a higher-level {@link TreeFormatter} into here, and get a coherent, nice error message printed out - so the user will see a nice chain of causes.
     */
    public void formatMessage(TreeFormatter formatter) {
        final String documentLink = new DocumentationRegistry()
            .getDocumentationRecommendationFor("on this", "dependency_verification", "sec:understanding-signature-verification");

        formatter.node(String.format("The following trusted GPG IDs are not in a minimum 160-bit fingerprint format (%s):", documentLink));
        formatter.startChildren();
        wrongKeys
            .stream()
            .map(key -> String.format("'%s'", key))
            .forEach(formatter::node);
        formatter.endChildren();
    }

    @Override
    public String getMessage() {
        final TreeFormatter treeFormatter = new TreeFormatter();
        formatMessage(treeFormatter);
        return treeFormatter.toString();
    }
}
