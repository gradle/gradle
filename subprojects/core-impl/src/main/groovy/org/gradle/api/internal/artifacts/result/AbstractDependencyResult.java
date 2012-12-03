/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class AbstractDependencyResult implements DependencyResult {

    private final ModuleVersionSelector requested;
    private final ResolvedModuleVersionResult selected;
    private final ResolvedModuleVersionResult from;

    public AbstractDependencyResult(ModuleVersionSelector requested, @Nullable ResolvedModuleVersionResult selected, ResolvedModuleVersionResult from) {
        assert requested != null;
        assert from != null;

        this.from = from;
        this.requested = requested;
        this.selected = selected;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Nullable
    public ResolvedModuleVersionResult getSelected() {
        return selected;
    }

    public ResolvedModuleVersionResult getFrom() {
        return from;
    }

    @Override
    public String toString() {
        return DependencyResultPrinter.print(this);
    }
}