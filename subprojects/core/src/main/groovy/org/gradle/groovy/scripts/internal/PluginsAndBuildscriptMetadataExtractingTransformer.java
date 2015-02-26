/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.plugin.use.internal.DefaultPluginRequests;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.plugin.use.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginRequestsSerializer;

public class PluginsAndBuildscriptMetadataExtractingTransformer implements MetadataExtractingTransformer<PluginRequests> {
    private final PluginsAndBuildscriptTransformer scriptBlockTransformer;
    private final StatementFilteringScriptTransformer classpathScriptTransformer;

    public PluginsAndBuildscriptMetadataExtractingTransformer(PluginsAndBuildscriptTransformer scriptBlockTransformer, StatementFilteringScriptTransformer classpathScriptTransformer) {
        this.scriptBlockTransformer = scriptBlockTransformer;
        this.classpathScriptTransformer = classpathScriptTransformer;
    }

    @Override
    public Transformer getTransformer() {
        return classpathScriptTransformer;
    }

    @Override
    public PluginRequests getExtractedMetadata() {
        return scriptBlockTransformer.getRequests();
    }

    @Override
    public PluginRequests getMetadataDefaultValue() {
        return new DefaultPluginRequests(ImmutableList.<PluginRequest>of());
    }

    @Override
    public Serializer<PluginRequests> getMetadataSerializer() {
        return new PluginRequestsSerializer();
    }
}
