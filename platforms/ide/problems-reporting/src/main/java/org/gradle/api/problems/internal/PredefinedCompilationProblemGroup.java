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

import org.gradle.api.problems.CompilationProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Compilation} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedCompilationProblemGroup extends CompilationProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Compilation";

    private final DefaultSecondLevelProblemGroup cpp = new DefaultSecondLevelProblemGroup("C++", PredefinedProblemGroupDescriptions.COMPILATION_CPP, this);
    private final DefaultSecondLevelProblemGroup groovy = new DefaultSecondLevelProblemGroup("Groovy", PredefinedProblemGroupDescriptions.COMPILATION_GROOVY, this);
    private final DefaultSecondLevelProblemGroup java = new DefaultSecondLevelProblemGroup("Java", PredefinedProblemGroupDescriptions.COMPILATION_JAVA, this);
    private final DefaultSecondLevelProblemGroup kotlin = new DefaultSecondLevelProblemGroup("Kotlin", PredefinedProblemGroupDescriptions.COMPILATION_KOTLIN, this);
    private final DefaultSecondLevelProblemGroup scala = new DefaultSecondLevelProblemGroup("Scala", PredefinedProblemGroupDescriptions.COMPILATION_SCALA, this);
    private final DefaultSecondLevelProblemGroup swift = new DefaultSecondLevelProblemGroup("Swift", PredefinedProblemGroupDescriptions.COMPILATION_SWIFT, this);
    private final DefaultUndefinedProblemGroup undefined = new DefaultUndefinedProblemGroup(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(cpp, groovy, java, kotlin, scala, swift, undefined);

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
        return PredefinedProblemGroupDescriptions.COMPILATION;
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
    public SecondLevelProblemGroup getCpp() {
        return cpp;
    }

    @Override
    public SecondLevelProblemGroup getGroovy() {
        return groovy;
    }

    @Override
    public SecondLevelProblemGroup getJava() {
        return java;
    }

    @Override
    public SecondLevelProblemGroup getKotlin() {
        return kotlin;
    }

    @Override
    public SecondLevelProblemGroup getScala() {
        return scala;
    }

    @Override
    public SecondLevelProblemGroup getSwift() {
        return swift;
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
