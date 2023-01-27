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
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;

public class InvalidGpgKeyIds extends GradleException {
    private final List<String> wrongKeys;

    public InvalidGpgKeyIds(List<String> wrongKeys) {
        this.wrongKeys = wrongKeys;
    }

    public void formatMessage(TreeFormatter formatter) {
        formatter.node("The following trusted GPG IDs are not in 160-bit fingerprint format (see: https://docs.gradle.org/current/userguide/dependency_verification.html#sec:understanding-signature-verification):");
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
