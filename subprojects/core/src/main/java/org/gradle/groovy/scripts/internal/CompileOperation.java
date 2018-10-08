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

import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.serialize.Serializer;

/**
 * A stateful “backing” for a compilation operation.
 * <p>
 * The compilation may extract data from the source under compilation, made available after compilation by {@link #getExtractedData()}.
 * The exposed transformer typically gathers the data while transforming.
 * <p>
 * As these objects are stateful, they can only be used for a single compile operation.
 *
 * @param <T> the type of data extracted by this operation
 */
public interface CompileOperation<T> {

    /**
     * A unique id for this operations.
     * <p>
     * Used to distinguish between the classes compiled from the same script with different transformers, so should be a valid java identifier.
     */
    String getId();

    /**
     * The stage of this compile operation.
     * This is exposed by {@link org.gradle.internal.scripts.CompileScriptBuildOperationType.Details#getStage()}.
     * */
    String getStage();

    Transformer getTransformer();

    /**
     * The data extracted from the script. Note that this method may be called without the transformer ever being invoked, in this case of an empty script.
     */
    T getExtractedData();

    Serializer<T> getDataSerializer();

}
