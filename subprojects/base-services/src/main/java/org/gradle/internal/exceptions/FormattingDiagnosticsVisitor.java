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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Formats candidates as a list.
 */
public class FormattingDiagnosticsVisitor implements DiagnosticsVisitor {
    private final Set<String> candidates = new LinkedHashSet<String>();

    public Set<String> getCandidates() {
        return candidates;
    }

    @Override
    public void candidate(String candidate) {
        candidates.add(candidate);
    }
}
