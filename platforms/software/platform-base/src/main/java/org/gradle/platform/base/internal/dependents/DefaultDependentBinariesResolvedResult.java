/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.internal.dependents;

import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

public class DefaultDependentBinariesResolvedResult implements DependentBinariesResolvedResult {

    private final LibraryBinaryIdentifier identifier;
    private final String projectScopeName;
    private final boolean buildable;
    private final boolean testSuite;
    private final List<DependentBinariesResolvedResult> children;

    public DefaultDependentBinariesResolvedResult(LibraryBinaryIdentifier identifier, String projectScopeName, boolean buildable, boolean testSuite, List<DependentBinariesResolvedResult> children) {
        checkNotNull(identifier, "Binary identifier must be non null");
        checkNotNull(emptyToNull(projectScopeName), "Name must be non empty");
        this.identifier = identifier;
        this.projectScopeName = projectScopeName;
        this.buildable = buildable;
        this.testSuite = testSuite;
        this.children = children != null ? children : Collections.<DependentBinariesResolvedResult>emptyList();
    }

    @Override
    public LibraryBinaryIdentifier getId() {
        return identifier;
    }

    @Override
    public String getProjectScopedName() {
        return projectScopeName;
    }

    @Override
    public boolean isBuildable() {
        return buildable;
    }

    @Override
    public boolean isTestSuite() {
        return testSuite;
    }

    @Override
    public List<DependentBinariesResolvedResult> getChildren() {
        return children;
    }

}
