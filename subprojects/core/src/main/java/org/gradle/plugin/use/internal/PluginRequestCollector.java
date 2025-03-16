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
import com.google.common.base.Strings;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.internal.InvalidPluginIdException;
import org.gradle.plugin.internal.InvalidPluginVersionException;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.PluginId;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * The real delegate of the plugins {} block.
 *
 * The {@link PluginUseScriptBlockMetadataCompiler} interacts with this type.
 */
public class PluginRequestCollector {
    public static final String EMPTY_VALUE = "cannot be null or empty";

    private final ScriptSource scriptSource;

    public PluginRequestCollector(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    private final List<PluginDependencySpecImpl> specs = new LinkedList<PluginDependencySpecImpl>();

    public PluginDependenciesSpec createSpec(final int pluginsBlockLineNumber) {
        return new PluginDependenciesSpecImpl(pluginsBlockLineNumber);
    }

    public PluginRequests getPluginRequests() {
        if (specs.isEmpty()) {
            return PluginRequests.EMPTY;
        }
        return PluginRequests.of(listPluginRequests());
    }

    @VisibleForTesting
    List<PluginRequestInternal> listPluginRequests() {
        List<PluginRequestInternal> pluginRequests = collect(specs, original ->
            new DefaultPluginRequest(original.id, original.apply, PluginRequestInternal.Origin.OTHER, scriptSource.getDisplayName(), original.lineNumber, original.version, null, null, null)
        );

        Map<PluginId, List<PluginRequestInternal>> groupedById = pluginRequests.stream().collect(groupingBy(PluginRequest::getId, Collectors.toList()));

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

    private class PluginDependenciesSpecImpl implements PluginDependenciesSpec {
        private final int blockLineNumber;

        public PluginDependenciesSpecImpl(int blockLineNumber) {
            this.blockLineNumber = blockLineNumber;
        }

        @Override
        public PluginDependencySpec id(String id) {
            return id(id, blockLineNumber);
        }

        public PluginDependencySpec id(String id, int requestLineNumber) {
            PluginDependencySpecImpl spec = new PluginDependencySpecImpl(id, requestLineNumber);
            specs.add(spec);
            return spec;
        }
    }

    private static class PluginDependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;
        private String version;
        private boolean apply;
        private final int lineNumber;

        private PluginDependencySpecImpl(String id, int lineNumber) {
            if (Strings.isNullOrEmpty(id)) {
                throw new InvalidPluginIdException(id, EMPTY_VALUE);
            }
            this.id = DefaultPluginId.of(id);
            this.apply = true;
            this.lineNumber = lineNumber;
        }

        @Override
        public PluginDependencySpec version(String version) {
            if (Strings.isNullOrEmpty(version)) {
                throw new InvalidPluginVersionException(version, EMPTY_VALUE);
            }
            this.version = version;
            return this;
        }

        @Override
        public PluginDependencySpec apply(boolean apply) {
            this.apply = apply;
            return this;
        }
    }

}
