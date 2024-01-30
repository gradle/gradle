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

package org.gradle.tooling.events.problems.internal;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.tooling.events.problems.Severity;

public class DefaultSeverity implements Severity {

    private final int severity;
    private final boolean known;

    // Using the loading cache ensures that there's only one object in memory per severity level even when the level is unknown by the client
    private static final LoadingCache<Integer, Severity> UNKNOWN_ENTRIES = CacheBuilder.newBuilder().build(new CacheLoader<Integer, Severity>() {
        @Override
        public Severity load(Integer key) {
            return new DefaultSeverity(key, false);
        }
    });

    public DefaultSeverity(int severity, boolean known) {
        this.severity = severity;
        this.known = known;
    }

    @Override
    public int getSeverity() {
        return severity;
    }

    @Override
    public boolean isKnown() {
        return known;
    }

    public static Severity from(int severity) {
        if (severity == Severity.ADVICE.getSeverity()) {
            return Severity.ADVICE;
        } else if (severity == Severity.WARNING.getSeverity()) {
            return Severity.WARNING;
        } else if (severity == Severity.ERROR.getSeverity()) {
            return Severity.ERROR;
        } else {
            return UNKNOWN_ENTRIES.getUnchecked(severity);
        }
    }
}
