/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.protocol.events;

import org.gradle.tooling.internal.protocol.InternalProtocolInterface;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 5.1
 */
public interface InternalJavaCompileTaskOperationResult extends InternalTaskResult {

    @Nullable
    List<InternalAnnotationProcessorResult> getAnnotationProcessorResults();

    interface InternalAnnotationProcessorResult extends InternalProtocolInterface {

        String TYPE_ISOLATING = "ISOLATING";
        String TYPE_AGGREGATING = "AGGREGATING";
        String TYPE_UNKNOWN = "UNKNOWN";

        String getClassName();

        String getType();

        Duration getDuration();

    }

}
