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

package org.gradle.api.internal.file;

import java.util.IdentityHashMap;
import java.util.function.Supplier;

/**
 * Performs an identity-based, path-copying substitution over structurally visible file collections.
 */
public final class FileCollectionSubstitution {
    private final FileCollectionInternal target;
    private final Supplier<FileCollectionInternal> replacementFactory;
    private final IdentityHashMap<FileCollectionInternal, FileCollectionInternal> substitutions = new IdentityHashMap<>();
    private FileCollectionInternal replacement;

    public FileCollectionSubstitution(FileCollectionInternal target, Supplier<FileCollectionInternal> replacementFactory) {
        this.target = target;
        this.replacementFactory = replacementFactory;
    }

    /**
     * Substitutes all structurally visible occurrences of the target in the given collection.
     * The replacement is created lazily and at most once.
     */
    public FileCollectionInternal substitute(FileCollectionInternal fileCollection) {
        if (fileCollection == target) {
            return replacement();
        }

        FileCollectionInternal substituted = substitutions.get(fileCollection);
        if (substituted != null) {
            return substituted;
        }

        if (!(fileCollection instanceof StructuralFileCollection)) {
            return fileCollection;
        }

        FileCollectionInternal result = ((StructuralFileCollection) fileCollection).substitute(this);
        substitutions.put(fileCollection, result);
        return result;
    }

    private FileCollectionInternal replacement() {
        if (replacement == null) {
            replacement = replacementFactory.get();
        }
        return replacement;
    }
}
