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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.gradle.api.problems.VerificationProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Verification} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedVerificationProblemGroup extends VerificationProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Verification";

    private final DefaultSecondLevelProblemGroup codeCoverage = new DefaultSecondLevelProblemGroup("Code Coverage", PredefinedProblemGroupDescriptions.VERIFICATION_CODE_COVERAGE, this);
    private final DefaultSecondLevelProblemGroup codeQuality = new DefaultSecondLevelProblemGroup("Code Quality", PredefinedProblemGroupDescriptions.VERIFICATION_CODE_QUALITY, this);
    private final DefaultSecondLevelProblemGroup security = new DefaultSecondLevelProblemGroup("Security", PredefinedProblemGroupDescriptions.VERIFICATION_SECURITY, this);
    private final DefaultSecondLevelProblemGroup testing = new DefaultSecondLevelProblemGroup("Testing", PredefinedProblemGroupDescriptions.VERIFICATION_TESTING, this);
    private final DefaultUndefinedProblemGroup undefined = new DefaultUndefinedProblemGroup(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(codeCoverage, codeQuality, security, testing, undefined);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return PredefinedProblemGroupDescriptions.VERIFICATION;
    }

    @Override
    @Nullable
    public ProblemGroup getParent() {
        return null;
    }

    @Override
    @Nullable
    public ProblemGroupInternal getParentInternal() {
        return null;
    }

    @Override
    public SecondLevelProblemGroup getCodeCoverage() {
        return codeCoverage;
    }

    @Override
    public SecondLevelProblemGroup getCodeQuality() {
        return codeQuality;
    }

    @Override
    public SecondLevelProblemGroup getSecurity() {
        return security;
    }

    @Override
    public SecondLevelProblemGroup getTesting() {
        return testing;
    }

    @Override
    public UndefinedProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public SecondLevelProblemGroup group(String name) {
        return children.group(this, name);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) {
        return children.resolve(this, name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupSupport.equals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupSupport.hashCode(this);
    }

    @Override
    public String toString() {
        return ProblemGroupSupport.render(this);
    }

    private Object writeReplace() {
        return SerializedProblemGroup.of(this);
    }
}
