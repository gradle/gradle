/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.PluginId;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.gradle.util.CollectionUtils.collect;

/**
 * The real delegate of the plugins {} block.
 *
 * The {@link PluginUseScriptBlockMetadataExtractor} interacts with this type.
 */
public class PluginRequestCollector {

    private final ScriptSource scriptSource;

    public PluginRequestCollector(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    private static class DependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;
        private String version;
        private boolean apply;
        private final int lineNumber;

        private DependencySpecImpl(String id, int lineNumber) {
            this.id = DefaultPluginId.of(id);
            this.apply = true;
            this.lineNumber = lineNumber;
        }

        @Override
        public PluginDependencySpec version(String version) {
            this.version = version;
            return this;
        }

        @Override
        public PluginDependencySpec apply(boolean apply) {
            this.apply = apply;
            return this;
        }
    }

    private final List<DependencySpecImpl> specs = new LinkedList<DependencySpecImpl>();

    public PluginDependenciesSpec createSpec(final int lineNumber) {
        return new PluginDependenciesSpec() {
            @Override
            public PluginDependencySpec id(String id) {
                DependencySpecImpl spec = new DependencySpecImpl(id, lineNumber);
                specs.add(spec);
                return spec;
            }
        };
    }

    public PluginRequests getPluginRequests() {
        if (specs.isEmpty()) {
            return DefaultPluginRequests.EMPTY;
        }
        return new DefaultPluginRequests(listPluginRequests());
    }

    @VisibleForTesting
    List<PluginRequestInternal> listPluginRequests() {
        List<PluginRequestInternal> pluginRequests = collect(specs, new Transformer<PluginRequestInternal, DependencySpecImpl>() {
            @Override
            public PluginRequestInternal transform(DependencySpecImpl original) {
                return new DefaultPluginRequest(original.id, original.version, original.apply, original.lineNumber, scriptSource);
            }
        });

        Map<PluginId, Collection<PluginRequestInternal>> groupedById = CollectionUtils.groupBy(pluginRequests, new Transformer<PluginId, PluginRequestInternal>() {
            @Override
            public PluginId transform(PluginRequestInternal pluginRequest) {
                return pluginRequest.getId();
            }
        });

        // Check for duplicates
        for (PluginId key : groupedById.keySet()) {
            Collection<PluginRequestInternal> pluginRequestsForId = groupedById.get(key);
            if (pluginRequestsForId.size() > 1) {
                Iterator<PluginRequestInternal> iterator = pluginRequestsForId.iterator();
                PluginRequestInternal first = iterator.next();
                PluginRequestInternal second = iterator.next();

                InvalidPluginRequestException exception = new InvalidPluginRequestException(second, "Plugin with id '" + key + "' was already requested at line " + first.getLineNumber());
                throw new LocationAwareException(exception, second.getScriptDisplayName(), second.getLineNumber());
            }
        }
        return pluginRequests;
    }

}
