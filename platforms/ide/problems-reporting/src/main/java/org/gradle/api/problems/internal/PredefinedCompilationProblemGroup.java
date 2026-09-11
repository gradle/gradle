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
import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SubProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Compilation} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedCompilationProblemGroup extends CompilationProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Compilation";

    private final DefaultSubProblemGroup cpp = new DefaultSubProblemGroup("C++", PredefinedProblemGroupDescriptions.COMPILATION_CPP, this);
    private final DefaultSubProblemGroup groovy = new DefaultSubProblemGroup("Groovy", PredefinedProblemGroupDescriptions.COMPILATION_GROOVY, this);
    private final DefaultSubProblemGroup java = new DefaultSubProblemGroup("Java", PredefinedProblemGroupDescriptions.COMPILATION_JAVA, this);
    private final DefaultSubProblemGroup kotlin = new DefaultSubProblemGroup("Kotlin", PredefinedProblemGroupDescriptions.COMPILATION_KOTLIN, this);
    private final DefaultSubProblemGroup scala = new DefaultSubProblemGroup("Scala", PredefinedProblemGroupDescriptions.COMPILATION_SCALA, this);
    private final DefaultSubProblemGroup swift = new DefaultSubProblemGroup("Swift", PredefinedProblemGroupDescriptions.COMPILATION_SWIFT, this);
    private final DefaultLeafProblemGroup undefined = DefaultLeafProblemGroup.undefined(this, NAME);
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
    public SubProblemGroup getCpp() {
        return cpp;
    }

    @Override
    public SubProblemGroup getGroovy() {
        return groovy;
    }

    @Override
    public SubProblemGroup getJava() {
        return java;
    }

    @Override
    public SubProblemGroup getKotlin() {
        return kotlin;
    }

    @Override
    public SubProblemGroup getScala() {
        return scala;
    }

    @Override
    public SubProblemGroup getSwift() {
        return swift;
    }

    @Override
    public LeafProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public SubProblemGroup group(String name) {
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
