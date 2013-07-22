/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern;

import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

/**
 * PatternMatcher for handling the very common '**\name' pattern
 * more efficiently than the DefaultPatternMatcher.
 *
 * This will only match against the last part of a relative path and this only if
 * the RelativePath is a File.
 */
public class NameOnlyPatternMatcher implements Spec<RelativePath> {
    private boolean partialMatchDirs;
    private PatternStep nameStep;

    /**
     * CTOR
     *
     * Note that pattern must be a single pattern step - i.e. can't contain
     * embedded '\' or '/'.  This is intended to match a file name.  It
     * can contain '*', '?' as wildcards.
     * @param partialMatchDirs
     * @param caseSensitive
     * @param pattern
     */
    public NameOnlyPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String pattern) {
        this.partialMatchDirs = partialMatchDirs;
        nameStep = PatternStepFactory.getStep(pattern, true, caseSensitive);
    }

    public boolean isSatisfiedBy(RelativePath path) {
        if (!path.isFile()) {
            return partialMatchDirs;
        }
        String lastName = path.getLastName();
        if (lastName == null) {
            return false;
        }
        return nameStep.matches(lastName, true);
    }
}
