/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.internal;

import org.gradle.api.Transformer;
import org.gradle.plugin.PluginDependenciesSpec;
import org.gradle.plugin.PluginDependencySpec;
import org.gradle.plugin.resolve.internal.DefaultPluginRequest;
import org.gradle.plugin.resolve.internal.PluginRequest;

import java.util.LinkedList;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

public class PluginDependenciesService {

    private static class DependencySpecImpl implements PluginDependencySpec {
        private final String id;
        private String version;

        private DependencySpecImpl(String id) {
            this.id = id;
        }

        public void version(String version) {
            this.version = version;
        }
    }

    private final List<DependencySpecImpl> specs = new LinkedList<DependencySpecImpl>();

    public PluginDependenciesSpec createSpec() {
        return new PluginDependenciesSpec() {
            public PluginDependencySpec id(String id) {
                DependencySpecImpl spec = new DependencySpecImpl(id);
                specs.add(spec);
                return spec;
            }
        };
    }

    public List<PluginRequest> getRequests() {
        return collect(specs, new Transformer<PluginRequest, DependencySpecImpl>() {
            public PluginRequest transform(DependencySpecImpl original) {
                return new DefaultPluginRequest(original.id, original.version);
            }
        });
    }

}
