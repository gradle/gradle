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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DefaultResolvedDependencyResult implements ResolvedDependencyResult {

    private final ModuleVersionSelector requested;
    private final DefaultResolvedModuleVersionResult selected;

    public DefaultResolvedDependencyResult(ModuleVersionSelector requested, ModuleVersionIdentifier selected) {
        assert requested != null;
        assert selected != null;
        this.requested = requested;
        this.selected = new DefaultResolvedModuleVersionResult(selected);
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public DefaultResolvedModuleVersionResult getSelected() {
        return selected;
    }

    @Override
    public String toString() {
        return ResolvedDependencyResultPrinter.print(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultResolvedDependencyResult)) {
            return false;
        }

        DefaultResolvedDependencyResult that = (DefaultResolvedDependencyResult) o;

        if (!requested.equals(that.requested)) {
            return false;
        }
        if (!selected.equals(that.selected)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = requested.hashCode();
        result = 31 * result + selected.hashCode();
        return result;
    }
}
