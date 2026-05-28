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
package org.gradle.security.internal;

import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Thrown when a PGP signature file (.asc) cannot be parsed, e.g. when it contains
 * invalid armor or otherwise malformed PGP packets.
 */
@NullMarked
public class InvalidSignatureFileException extends RuntimeException {
    private final File signatureFile;

    public InvalidSignatureFileException(File signatureFile, Throwable cause) {
        super("Could not read signatures from " + signatureFile + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        this.signatureFile = signatureFile;
    }

    public File getSignatureFile() {
        return signatureFile;
    }
}
