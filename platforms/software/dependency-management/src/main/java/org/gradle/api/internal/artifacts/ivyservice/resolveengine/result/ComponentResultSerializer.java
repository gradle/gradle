/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

/**
 * Serializes a components from a resolved graph so they can be loaded back if requested
 * by the user.
 */
public interface ComponentResultSerializer {

    /**
     * Serialize the component using the encoder.
     */
    void writeComponentResult(Encoder encoder, ResolvedGraphComponent component, boolean includeAllSelectableVariantResults) throws Exception;

    /**
     * Deserialize the component from the decoder, passing the result to the visitor.
     */
    void readComponentResult(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception;
}
