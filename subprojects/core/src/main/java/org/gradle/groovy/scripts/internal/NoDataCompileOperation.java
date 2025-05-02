/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.serialize.Serializer;

/**
 * A {@link CompileOperation} that does not extract or persist any data.
 */
public class NoDataCompileOperation implements CompileOperation<Object> {

    private final String id;
    private final String stage;
    private final Transformer transformer;

    public NoDataCompileOperation(String id, String stage, Transformer transformer) {
        this.id = id;
        this.stage = stage;
        this.transformer = transformer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getStage() {
        return stage;
    }

    @Override
    public Transformer getTransformer() {
        return transformer;
    }

    @Override
    public Object getExtractedData() {
        return null;
    }

    @Override
    public Serializer<Object> getDataSerializer() {
        return null;
    }
}
