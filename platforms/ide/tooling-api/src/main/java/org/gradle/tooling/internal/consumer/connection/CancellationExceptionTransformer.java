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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;

public abstract class CancellationExceptionTransformer {

    private static final CancellationExceptionTransformer NO_OP = new CancellationExceptionTransformer() {
        @Override
        public RuntimeException transform(RuntimeException e) {
            return e;
        }
    };

    static CancellationExceptionTransformer transformerFor(VersionDetails versionDetails) {
        if (versionDetails.honorsContractOnCancel()) {
            return NO_OP;
        }
        return new CancellationExceptionTransformer() {
            @Override
            public RuntimeException transform(RuntimeException e) {
                for (Throwable t = e; t != null; t = t.getCause()) {
                    if ("org.gradle.api.BuildCancelledException".equals(t.getClass().getName())
                        || "org.gradle.tooling.BuildCancelledException".equals(t.getClass().getName())) {
                        return new InternalBuildCancelledException(e.getCause());
                    }
                }
                return e;
            }
        };
    }

    abstract RuntimeException transform(RuntimeException e);
}
