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


package org.gradle.api.internal.provider.provenance;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered mutations that produced a property's current configuration.
 * <p>
 * Allocated only once a property is mutated a <em>second</em> time; a single mutation is represented by the
 * interned {@link MutationRecord} itself. Bounded, so that a property mutated in a loop cannot grow without
 * limit. Mutations are kept even once superseded by a replacing {@code set}: for diagnostics, "plugin A set it
 * and then the build script overwrote it" is the interesting fact.
 */
public final class MutationTrace implements MutationHistory {

    /**
     * Beyond this many mutations, later ones are counted but not retained.
     */
    static final int MAX_RECORDS = 32;

    private final List<MutationRecord> records = new ArrayList<>(4);
    private int notRetained;

    public void add(MutationRecord record) {
        if (records.size() < MAX_RECORDS) {
            records.add(record);
        } else {
            notRetained++;
        }
    }

    /**
     * The mutations in the order they happened, oldest first.
     */
    @Override
    public List<MutationRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    /**
     * How many mutations happened beyond the ones retained.
     */
    @Override
    public int getNotRetainedCount() {
        return notRetained;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * The last mutation of the explicit value, if any.
     */
    @Override
    public @Nullable MutationRecord lastExplicit() {
        return last(false);
    }

    /**
     * The last mutation of the convention, if any.
     */
    @Override
    public @Nullable MutationRecord lastConvention() {
        return last(true);
    }

    private @Nullable MutationRecord last(boolean convention) {
        for (int i = records.size() - 1; i >= 0; i--) {
            MutationRecord record = records.get(i);
            if (record.getKind().isConvention() == convention) {
                return record;
            }
        }
        return null;
    }

    /**
     * A single retained mutation reads as one sentence; several read as an ordered list.
     */
    @Override
    public String describeForMessage() {
        List<MutationRecord> attributed = new ArrayList<>(records.size());
        for (MutationRecord record : records) {
            if (record.isAttributed()) {
                attributed.add(record);
            }
        }
        if (attributed.isEmpty()) {
            return "";
        }
        if (attributed.size() == 1 && notRetained == 0) {
            return " It was last " + attributed.get(0).describe() + ".";
        }
        StringBuilder result = new StringBuilder("\nIt was configured by, in order:");
        for (int i = 0; i < attributed.size(); i++) {
            result.append("\n  ").append(i + 1).append(". ").append(attributed.get(i).describe());
        }
        if (notRetained > 0) {
            result.append("\n  and ").append(notRetained).append(" later mutation(s) not retained.");
        }
        return result.toString();
    }
}
