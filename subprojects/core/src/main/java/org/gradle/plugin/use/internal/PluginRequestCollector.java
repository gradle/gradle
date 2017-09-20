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

import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Pair;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.dsl.BinaryPluginDependencySpec;
import org.gradle.plugin.dsl.PluginDependenciesSpec;
import org.gradle.plugin.dsl.ScriptPluginDependencySpec;
import org.gradle.plugin.management.internal.BinaryPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.ScriptPluginRequest;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.gradle.util.CollectionUtils.collect;

/**
 * The real delegate of the plugins {} block.
 *
 * The {@link PluginUseScriptBlockMetadataExtractor} interacts with this type for the Groovy DSL. The Kotlin DSL also interacts with this type.
 */
public class PluginRequestCollector {

    private final ScriptSource scriptSource;

    public PluginRequestCollector(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    private static class BinaryDependencySpecImpl implements org.gradle.plugin.use.PluginDependencySpec {
        private final int lineNumber;
        private final PluginId id;
        private String version;
        private boolean apply = true;

        private BinaryDependencySpecImpl(int lineNumber, String id) {
            this.lineNumber = lineNumber;
            this.id = id == null ? null : DefaultPluginId.of(id);
        }

        @Override
        public org.gradle.plugin.use.PluginDependencySpec version(String version) {
            this.version = version;
            return this;
        }

        @Override
        public org.gradle.plugin.use.PluginDependencySpec apply(boolean apply) {
            this.apply = apply;
            return this;
        }
    }

    private static class ScriptDependencySpecImpl implements org.gradle.plugin.use.ScriptPluginDependencySpec {
        private final int lineNumber;
        private String script;

        private ScriptDependencySpecImpl(int lineNumber, String script) {
            this.lineNumber = lineNumber;
            this.script = script;
        }
    }

    private static class BinaryPluginDependencySpecImpl implements BinaryPluginDependencySpec {
        private final int lineNumber;
        private final PluginId id;
        private String version;
        private boolean apply = true;

        private BinaryPluginDependencySpecImpl(int lineNumber, String id) {
            this.lineNumber = lineNumber;
            this.id = id == null ? null : DefaultPluginId.of(id);
        }

        @Override
        public BinaryPluginDependencySpec version(@Nullable String version) {
            this.version = version;
            return this;
        }

        @Override
        public BinaryPluginDependencySpec apply(boolean apply) {
            this.apply = apply;
            return this;
        }
    }

    private static class ScriptPluginDependencySpecImpl implements ScriptPluginDependencySpec {
        private final int lineNumber;
        private String script;

        private ScriptPluginDependencySpecImpl(int lineNumber, String script) {
            this.lineNumber = lineNumber;
            this.script = script;
        }
    }

    private final List<Object> specs = new LinkedList<Object>();

    @Deprecated
    public org.gradle.plugin.use.PluginDependenciesSpec createSpec(final int lineNumber) {
        return new org.gradle.plugin.use.PluginDependenciesSpec() {
            public org.gradle.plugin.use.PluginDependencySpec id(String id) {
                org.gradle.plugin.use.PluginDependencySpec pluginDependency = new BinaryDependencySpecImpl(lineNumber, id);
                specs.add(pluginDependency);
                return pluginDependency;
            }

            @Override
            public org.gradle.plugin.use.ScriptPluginDependencySpec script(String script) {
                org.gradle.plugin.use.ScriptPluginDependencySpec pluginDependency = new ScriptDependencySpecImpl(lineNumber, script);
                specs.add(pluginDependency);
                return pluginDependency;
            }
        };
    }

    public PluginDependenciesSpec createPluginDependencySpec(final int lineNumber) {
        return new PluginDependenciesSpec() {
            @Override
            public BinaryPluginDependencySpec id(String id) {
                BinaryPluginDependencySpec pluginDependency = new BinaryPluginDependencySpecImpl(lineNumber, id);
                specs.add(pluginDependency);
                return pluginDependency;
            }

            @Override
            public ScriptPluginDependencySpec script(String script) {
                ScriptPluginDependencySpec pluginDependency = new ScriptPluginDependencySpecImpl(lineNumber, script);
                specs.add(pluginDependency);
                return pluginDependency;
            }
        };
    }

    public PluginRequests getPluginRequests() {
        return new DefaultPluginRequests(listPluginRequests());
    }

    public List<PluginRequestInternal> listPluginRequests() {
        List<PluginRequestInternal> pluginRequests = collectPluginRequests();
        checkForDuplicates(pluginRequests);
        return pluginRequests;
    }

    private List<PluginRequestInternal> collectPluginRequests() {
        return collect(specs, new Transformer<PluginRequestInternal, Object>() {
            public PluginRequestInternal transform(Object original) {
                return pluginRequestFor(original);
            }
        });
    }

    private PluginRequestInternal pluginRequestFor(Object pluginDependency) {
        if (pluginDependency instanceof BinaryPluginDependencySpecImpl) {
            return pluginRequestFor((BinaryPluginDependencySpecImpl) pluginDependency);
        } else if (pluginDependency instanceof ScriptPluginDependencySpecImpl) {
            return pluginRequestFor((ScriptPluginDependencySpecImpl) pluginDependency);
        } else if (pluginDependency instanceof BinaryDependencySpecImpl) {
            return pluginRequestFor((BinaryDependencySpecImpl) pluginDependency);
        } else if (pluginDependency instanceof ScriptDependencySpecImpl) {
            return pluginRequestFor((ScriptDependencySpecImpl) pluginDependency);
        } else {
            throw new IllegalStateException("Unknown PluginDependencySpec: " + pluginDependency);
        }
    }

    private PluginRequestInternal pluginRequestFor(BinaryPluginDependencySpecImpl binaryPluginDependency) {
        return new BinaryPluginRequest(
            scriptSource,
            binaryPluginDependency.lineNumber,
            binaryPluginDependency.id,
            binaryPluginDependency.version,
            binaryPluginDependency.apply,
            null);
    }

    private PluginRequestInternal pluginRequestFor(ScriptPluginDependencySpecImpl scriptPluginDependency) {
        return new ScriptPluginRequest(
            scriptSource,
            scriptPluginDependency.lineNumber,
            URI.create(scriptPluginDependency.script));
    }

    private PluginRequestInternal pluginRequestFor(BinaryDependencySpecImpl binaryPluginDependency) {
        return new BinaryPluginRequest(
            scriptSource,
            binaryPluginDependency.lineNumber,
            binaryPluginDependency.id,
            binaryPluginDependency.version,
            binaryPluginDependency.apply,
            null);
    }

    private PluginRequestInternal pluginRequestFor(ScriptDependencySpecImpl scriptPluginDependency) {
        return new ScriptPluginRequest(
            scriptSource,
            scriptPluginDependency.lineNumber,
            URI.create(scriptPluginDependency.script));
    }

    private void checkForDuplicates(List<PluginRequestInternal> pluginRequests) {
        Map<Pair<String, Object>, PluginRequestInternal> requestMap = new HashMap<Pair<String, Object>, PluginRequestInternal>(pluginRequests.size());
        for (PluginRequestInternal request : pluginRequests) {
            Pair<String, Object> key = keyFor(request);
            PluginRequestInternal existing = requestMap.get(key);
            if (existing != null) {
                InvalidPluginRequestException exception = new InvalidPluginRequestException(request, key.left + " '" + key.right + "' was already requested at line " + existing.getRequestingScriptLineNumber());
                throw new LocationAwareException(exception, request.getRequestingScriptDisplayName(), request.getRequestingScriptLineNumber());
            }
            requestMap.put(key, request);
        }
    }

    private Pair<String, Object> keyFor(PluginRequestInternal pluginRequest) {
        PluginId id = pluginRequest.getId();
        if (id != null) {
            return Pair.of("Plugin with id", (Object) id);
        }
        URI script = pluginRequest.getScript();
        if (script != null) {
            return Pair.of("Script Plugin", (Object) script);
        }
        throw new IllegalStateException(pluginRequest + " is neither a binary or script plugin request.");
    }

}
