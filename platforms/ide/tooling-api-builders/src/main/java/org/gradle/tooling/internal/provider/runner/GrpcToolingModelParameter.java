/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.jspecify.annotations.NullMarked;

/**
 * The parameter object the native gRPC path hands to a {@code ParameterizedToolingModelBuilder}.
 *
 * <p>It carries the client's parameter opaquely as the bytes of a {@code google.protobuf.Any}, so
 * Gradle never needs the vendor's parameter schema. The tooling model parameter carrier adapts this
 * onto the builder's declared parameter interface structurally (by the {@code getParameterBytes}
 * getter), exactly as the model result is adapted onto the client's view interface; the plugin's
 * builder then unpacks the Any itself. Implementing a single interface keeps the carrier's
 * property-hashing (which requires exactly one interface) happy.</p>
 */
@NullMarked
public interface GrpcToolingModelParameter {

    byte[] getParameterBytes();

    static GrpcToolingModelParameter of(byte[] parameterBytes) {
        return new GrpcToolingModelParameter() {
            @Override
            public byte[] getParameterBytes() {
                return parameterBytes;
            }
        };
    }
}
