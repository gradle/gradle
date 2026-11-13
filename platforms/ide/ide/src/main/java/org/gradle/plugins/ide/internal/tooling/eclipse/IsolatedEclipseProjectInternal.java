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
package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * The part of a project's Eclipse model that other projects need in order to build theirs.
 *
 * <p>This is an intermediate model: it is built for each project separately, under that project's own
 * lock, and the results are then combined by the builder of the whole {@code EclipseProject} model.
 * That is what makes the combined model safe to build with Isolated Projects enabled, where reaching
 * into another project directly is not allowed.
 *
 * <p>Only the module path flag lives here so far. More of the per-project data that
 * {@code EclipseModelBuilder} currently reads across project boundaries can move here as it is made
 * Isolated Projects safe.
 */
@NullMarked
public class IsolatedEclipseProjectInternal implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean inferModulePath;

    public IsolatedEclipseProjectInternal(boolean inferModulePath) {
        this.inferModulePath = inferModulePath;
    }

    /**
     * Whether the sources of this project form a Java module, so that a project dependency on it
     * belongs on the module path rather than the classpath.
     */
    public boolean isInferModulePath() {
        return inferModulePath;
    }

    @Override
    public String toString() {
        return "IsolatedEclipseProjectInternal{inferModulePath=" + inferModulePath + '}';
    }
}
