/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.gradle.internal.changes.FileChange;
import org.gradle.internal.changes.TaskStateChangeVisitor;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;

import java.util.Collections;
import java.util.Map;

public class EmptyHistoricalFileCollectionFingerprint implements HistoricalFileCollectionFingerprint {
    public static final EmptyHistoricalFileCollectionFingerprint INSTANCE = new EmptyHistoricalFileCollectionFingerprint();

    private EmptyHistoricalFileCollectionFingerprint() {
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, final String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        for (Map.Entry<String, FileSystemLocationFingerprint> entry : oldFingerprint.getFingerprints().entrySet()) {
            if (!visitor.visitChange(FileChange.removed(entry.getKey(), title, entry.getValue().getType()))) {
                return false;
            }
        }
        return true;
    }

    public Multimap<String, HashCode> getRootHashes() {
        return ImmutableMultimap.of();
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> getFingerprints() {
        return Collections.emptyMap();
    }

    @Override
    public String toString() {
        return "EMPTY";
    }
}
