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

import org.gradle.api.problems.DocumentationProblemGroup;
import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SubProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Documentation} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedDocumentationProblemGroup extends DocumentationProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Documentation";

    private final DefaultSubProblemGroup javadoc = new DefaultSubProblemGroup("Javadoc", PredefinedProblemGroupDescriptions.DOCUMENTATION_JAVADOC, this);
    private final DefaultSubProblemGroup groovydoc = new DefaultSubProblemGroup("Groovydoc", PredefinedProblemGroupDescriptions.DOCUMENTATION_GROOVYDOC, this);
    private final DefaultSubProblemGroup scaladoc = new DefaultSubProblemGroup("Scaladoc", PredefinedProblemGroupDescriptions.DOCUMENTATION_SCALADOC, this);
    private final DefaultLeafProblemGroup undefined = DefaultLeafProblemGroup.undefined(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(javadoc, groovydoc, scaladoc, undefined);

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
        return PredefinedProblemGroupDescriptions.DOCUMENTATION;
    }

    @Override
    @Nullable
    public ProblemGroup getParent() {
        return null;
    }

    @Override
    public SubProblemGroup getJavadoc() {
        return javadoc;
    }

    @Override
    public SubProblemGroup getGroovydoc() {
        return groovydoc;
    }

    @Override
    public SubProblemGroup getScaladoc() {
        return scaladoc;
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
