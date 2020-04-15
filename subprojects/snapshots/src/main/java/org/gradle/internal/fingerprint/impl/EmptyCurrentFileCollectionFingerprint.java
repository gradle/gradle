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
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.util.Collections;
import java.util.Map;

public class EmptyCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private static final HashCode SIGNATURE = Hashing.signature(EmptyCurrentFileCollectionFingerprint.class);

    private final String identifier;

    public EmptyCurrentFileCollectionFingerprint(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> getFingerprints() {
        return Collections.emptyMap();
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
    }

    @Override
    public ImmutableMultimap<String, HashCode> getRootHashes() {
        return ImmutableMultimap.of();
    }

    @Override
    public ImmutableSet<String> getRootPaths() {
        return ImmutableSet.of();
    }

    @Override
    public String getStrategyIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return identifier + "{EMPTY}";
    }
}
