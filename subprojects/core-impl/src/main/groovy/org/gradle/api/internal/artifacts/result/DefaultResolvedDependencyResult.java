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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DefaultResolvedDependencyResult implements ResolvedDependencyResult {

    private final Set<String> configurations = new LinkedHashSet<String>();
    private final SelectionId selection;

    public DefaultResolvedDependencyResult(ModuleVersionSelector requested, ModuleVersionIdentifier selected, Collection<String> configurations) {
        assert requested != null;
        assert selected != null;
        assert configurations != null;
        selection = new SelectionId(requested, new DefaultResolvedModuleVersionResult(selected));
        this.configurations.addAll(configurations);
    }

    public ModuleVersionSelector getRequested() {
        return selection.requested;
    }

    public DefaultResolvedModuleVersionResult getSelected() {
        return selection.selected;
    }

    public Set<String> getSelectedConfigurations() {
        return configurations;
    }

    public void appendConfigurations(Set<String> configurations) {
        this.configurations.addAll(configurations);
    }

    //we merge the configurations into dependency results that have the same 'selectionId'
    //(a combination of 'selected' and 'requested' )
    public Object getSelectionId() {
        return selection;
    }

    private static class SelectionId {
        final ModuleVersionSelector requested;
        final DefaultResolvedModuleVersionResult selected;

        public SelectionId(ModuleVersionSelector requested, DefaultResolvedModuleVersionResult selected) {
            this.requested = requested;
            this.selected = selected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SelectionId)) {
                return false;
            }

            SelectionId selection = (SelectionId) o;

            if (!requested.equals(selection.requested)) {
                return false;
            }
            if (!selected.equals(selection.selected)) {
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

        if (!configurations.equals(that.configurations)) {
            return false;
        }
        if (!selection.equals(that.selection)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = configurations.hashCode();
        result = 31 * result + selection.hashCode();
        return result;
    }
}
