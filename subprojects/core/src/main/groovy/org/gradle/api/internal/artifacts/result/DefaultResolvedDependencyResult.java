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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DefaultResolvedDependencyResult implements ResolvedDependencyResult {

    private final ModuleVersionSelector requested;
    private final DefaultResolvedModuleVersionResult selected;
    private final DefaultResolvedModuleVersionResult from;

    public DefaultResolvedDependencyResult(ModuleVersionSelector requested, DefaultResolvedModuleVersionResult selected, DefaultResolvedModuleVersionResult from) {
        assert requested != null;
        assert selected != null;
        assert from != null;

        this.from = from;
        this.requested = requested;
        this.selected = selected;
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

        if (!from.equals(that.from)) {
            return false;
        }
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
        result = 31 * result + from.hashCode();
        return result;
    }

    public DefaultResolvedModuleVersionResult getFrom() {
        return from;
    }
}
