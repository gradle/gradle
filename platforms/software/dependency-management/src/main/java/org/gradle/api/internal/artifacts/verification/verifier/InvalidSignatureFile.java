/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.verifier;

import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Verification failure raised when the signature file (.asc) cannot be parsed
 * (e.g. it has invalid ASCII armor or otherwise corrupt PGP packets).
 */
@NullMarked
public class InvalidSignatureFile extends AbstractVerificationFailure {
    private final File signatureFile;
    private final String causeDescription;

    public InvalidSignatureFile(File affectedFile, File signatureFile, String causeDescription) {
        super(affectedFile);
        this.signatureFile = signatureFile;
        this.causeDescription = causeDescription;
    }

    @Override
    public File getSignatureFile() {
        return signatureFile;
    }

    public String getCauseDescription() {
        return causeDescription;
    }

    @Override
    public boolean isFatal() {
        return true;
    }

    @Override
    public void explainTo(TreeFormatter formatter) {
        formatter.append("signature file is corrupt (").append(causeDescription).append(")");
    }
}
