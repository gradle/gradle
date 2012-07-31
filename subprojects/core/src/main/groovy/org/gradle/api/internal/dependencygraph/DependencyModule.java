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

package org.gradle.api.internal.dependencygraph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DependencyModule {

    ModuleVersionIdentifier asked;
    ModuleVersionIdentifier selected;
    private final Set<String> configurations = new LinkedHashSet<String>();

    public DependencyModule(ModuleVersionIdentifier asked, ModuleVersionIdentifier selected, Set<String> configurations) {
        this.asked = asked;
        this.selected = selected;
        this.configurations.addAll(configurations);
    }

    @Override
    public String toString() {
        if (!asked.equals(selected)) {
            //TODO SF the report should not depend on the toString()
            return asked() + " -> " + selected.getVersion();
        } else {
            return asked();
        }
    }

    private String asked() {
        return asked.getGroup() + ":" + asked.getName() + ":" + asked.getVersion();
    }

    public ModuleVersionIdentifier getAsked() {
        return asked;
    }

    public ModuleVersionIdentifier getSelected() {
        return selected;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }

    public void appendConfigurations(Set<String> configurations) {
        this.configurations.addAll(configurations);
    }

    //TODO SF tests
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DependencyModule that = (DependencyModule) o;

        if (asked != null ? !asked.equals(that.asked) : that.asked != null) {
            return false;
        }
        if (configurations != null ? !configurations.equals(that.configurations) : that.configurations != null) {
            return false;
        }
        if (selected != null ? !selected.equals(that.selected) : that.selected != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = asked != null ? asked.hashCode() : 0;
        result = 31 * result + (selected != null ? selected.hashCode() : 0);
        result = 31 * result + (configurations != null ? configurations.hashCode() : 0);
        return result;
    }
}
