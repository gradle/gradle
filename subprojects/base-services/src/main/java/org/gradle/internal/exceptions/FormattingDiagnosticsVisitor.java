/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.exceptions;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formats candidates as a list.
 */
public class FormattingDiagnosticsVisitor implements DiagnosticsVisitor {
    private final Map<String, Candidate> candidates = new LinkedHashMap<String, Candidate>();
    private Candidate current;

    public List<String> getCandidates() {
        return format(candidates);
    }

    private List<String> format(Map<String, Candidate> candidates) {
        List<String> formatted = new ArrayList<String>();
        for (Candidate candidate : candidates.values()) {
            if (candidate.examples.isEmpty()) {
                formatted.add(candidate.description);
            } else {
                formatted.add(String.format("%s, for example %s.", candidate.description, Joiner.on(", ").join(candidate.examples)));
            }
        }
        return formatted;
    }

    @Override
    public DiagnosticsVisitor candidate(String displayName) {
        Candidate candidate = candidates.get(displayName);
        if (candidate == null) {
            candidate = new Candidate(displayName);
            candidates.put(displayName, candidate);
        }
        current = candidate;
        return this;
    }

    @Override
    public DiagnosticsVisitor example(String example) {
        current.examples.add(example);
        return this;
    }

    @Override
    public DiagnosticsVisitor values(Iterable<?> values) {
        return this;
    }

    private static class Candidate {
        final String description;
        final List<String> examples = new ArrayList<String>();

        public Candidate(String description) {
            this.description = description;
        }
    }
}
