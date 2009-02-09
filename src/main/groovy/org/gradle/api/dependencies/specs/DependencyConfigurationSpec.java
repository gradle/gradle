/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.dependencies.specs;

import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.specs.Spec;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DependencyConfigurationSpec<T extends Dependency> implements Spec<T> {
    boolean includeExtendees;
    private List<String> confs;

    public DependencyConfigurationSpec(boolean includeExtendees, String... confs) {
        this.includeExtendees = includeExtendees;
        this.confs = Arrays.asList(confs);
    }

    public boolean isSatisfiedBy(Dependency dependency) {
        for (String conf : confs) {
            for (Configuration configuration : getConfigurations(dependency, includeExtendees)) {
                    if (configuration.getName().equals(conf)) {
                        return true;
                    }
            }
        }
        return false;
    }

    private Set<Configuration> getConfigurations(Dependency dependency, boolean includeExtendees) {
        Set<Configuration> result = new HashSet<Configuration>();
        for (Configuration configuration : dependency.getConfigurations()) {
            if (includeExtendees) {
                result.addAll(configuration.getChain());
            } else {
                result.add(configuration);
            }
        }
        return result;
    }

    public List<String> getConfs() {
        return Collections.unmodifiableList(confs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DependencyConfigurationSpec confSpec = (DependencyConfigurationSpec) o;

        if (includeExtendees != confSpec.includeExtendees) return false;
        if (confs != null ? !confs.equals(confSpec.confs) : confSpec.confs != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (includeExtendees ? 1 : 0);
        result = 31 * result + (confs != null ? confs.hashCode() : 0);
        return result;
    }
}
