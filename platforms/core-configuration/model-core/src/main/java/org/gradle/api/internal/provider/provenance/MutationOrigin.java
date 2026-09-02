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

import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

/**
 * Where a property mutation came from: a stable contributor, plus enough description of the code that was
 * running to be useful in an error message.
 * <p>
 * Origins are interned by {@link MutationOriginRegistry}, so a mutation record can be shared by every property
 * that a given plugin mutates the same way.
 */
public final class MutationOrigin {

    public static final MutationOrigin UNKNOWN = new MutationOrigin(ContributorKey.UNKNOWN, "unknown code");

    private final ContributorKey contributor;
    private final String displayName;

    public MutationOrigin(ContributorKey contributor, String displayName) {
        this.contributor = contributor;
        this.displayName = displayName;
    }

    public static MutationOrigin of(@Nullable UserCodeSource source) {
        return source == null
            ? UNKNOWN
            : new MutationOrigin(ContributorKey.of(source), source.getDisplayName().getDisplayName());
    }

    public ContributorKey getContributor() {
        return contributor;
    }

    /**
     * How to name this origin to a user, for example {@code plugin 'com.example.feature'} or
     * {@code build file 'build.gradle'}. Taken from the user code source, so it matches the wording that
     * task provenance already uses.
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
