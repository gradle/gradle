/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

public class ResolvedComponentResultSerializer implements Serializer<ResolvedComponentResult> {

    @Override
    public ResolvedComponentResult read(Decoder decoder) throws IOException {
        DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
        // TODO:configuration-cache try to reuse StreamingResolutionResultBuilder.RootFactory
        return builder.complete(1L).getRoot();
    }

    @Override
    public void write(Encoder encoder, ResolvedComponentResult value) throws IOException {
        // TODO:configuration-cache visit selectors, components, dependencies as in StreamingResolutionResultBuilder
    }
}
