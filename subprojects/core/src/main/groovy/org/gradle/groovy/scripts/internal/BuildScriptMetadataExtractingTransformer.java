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

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.messaging.serialize.Serializer;

public class BuildScriptMetadataExtractingTransformer implements MetadataExtractingTransformer<Boolean> {

    private final ImperativeStatementDetectingTransformer transformer;

    public BuildScriptMetadataExtractingTransformer(String filteringTransformerId, Transformer filteringTransformer, String classpathClosureName, ScriptSource scriptSource) {
        String buildScriptTransformerId = "no_" + filteringTransformerId;
        BuildScriptTransformer buildScriptTransformer = new BuildScriptTransformer(buildScriptTransformerId, filteringTransformer, scriptSource);
        transformer = new ImperativeStatementDetectingTransformer("metadata_" + buildScriptTransformerId, buildScriptTransformer, classpathClosureName);
    }

    @Override
    public Transformer getTransformer() {
        return transformer;
    }

    @Override
    public Boolean getExtractedMetadata() {
        return transformer.isImperativeStatementDetected();
    }

    @Override
    public Boolean getMetadataDefaultValue() {
        return Boolean.FALSE;
    }

    @Override
    public Serializer<Boolean> getMetadataSerializer() {
        return new BooleanSerializer();
    }
}
