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

package org.gradle.plugin.use.internal;

import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;

import java.util.LinkedList;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

/**
 * The real delegate of the plugins {} block.
 *
 * The PluginUseScriptBlockTransformer interacts with this type.
 */
public class PluginDependenciesService {

    private final ScriptSource scriptSource;

    public PluginDependenciesService(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    private static class DependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;
        private String version;
        private final int lineNumber;

        private DependencySpecImpl(String id, int lineNumber) {
            this.id = PluginId.of(id);
            this.lineNumber = lineNumber;
        }

        public void version(String version) {
            this.version = version;
        }
    }

    private final List<DependencySpecImpl> specs = new LinkedList<DependencySpecImpl>();

    public PluginDependenciesSpec createSpec(final int lineNumber) {
        return new PluginDependenciesSpec() {
            public PluginDependencySpec id(String id) {
                DependencySpecImpl spec = new DependencySpecImpl(id, lineNumber);
                specs.add(spec);
                return spec;
            }
        };
    }

    public List<PluginRequest> getRequests() {
        return collect(specs, new Transformer<PluginRequest, DependencySpecImpl>() {
            public PluginRequest transform(DependencySpecImpl original) {
                return new DefaultPluginRequest(original.id, original.version, original.lineNumber, scriptSource);
            }
        });
    }

}
