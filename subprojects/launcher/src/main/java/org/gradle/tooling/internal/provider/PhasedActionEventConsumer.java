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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.protocol.AfterBuildResult;
import org.gradle.tooling.internal.protocol.AfterConfigurationResult;
import org.gradle.tooling.internal.protocol.AfterLoadingResult;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import javax.annotation.Nullable;

/**
 * Consumer of events from phased actions. This consumer deserializes the results and forward them to the correct listener.
 */
public class PhasedActionEventConsumer implements BuildEventConsumer {
    private final PhasedActionResultListener phasedActionResultListener;
    private final PayloadSerializer payloadSerializer;
    private final BuildEventConsumer delegate;

    PhasedActionEventConsumer(PhasedActionResultListener phasedActionResultListener, PayloadSerializer payloadSerializer, BuildEventConsumer delegate) {
        this.phasedActionResultListener = phasedActionResultListener;
        this.payloadSerializer = payloadSerializer;
        this.delegate = delegate;
    }

    @Override
    public void dispatch(final Object event) {
        if (event instanceof PhasedBuildActionResult) {
            PhasedBuildActionResult resultEvent = (PhasedBuildActionResult) event;
            final Object deserializedResult = resultEvent.result == null ? null : payloadSerializer.deserialize(resultEvent.result);
            final Throwable deserializedFailure = resultEvent.failure == null ? null : (Throwable) payloadSerializer.deserialize(resultEvent.failure);

            PhasedActionResult<?> phasedActionResult = null;
            if (resultEvent.type == PhasedBuildActionResult.Type.AFTER_LOADING) {
                phasedActionResult = new AfterLoadingResult<Object>() {
                    @Nullable
                    @Override
                    public Object getResult() {
                        return deserializedResult;
                    }

                    @Nullable
                    @Override
                    public Throwable getFailure() {
                        return deserializedFailure;
                    }
                };
            } else if (resultEvent.type == PhasedBuildActionResult.Type.AFTER_CONFIGURATION) {
                phasedActionResult = new AfterConfigurationResult<Object>() {
                    @Nullable
                    @Override
                    public Object getResult() {
                        return deserializedResult;
                    }

                    @Nullable
                    @Override
                    public Throwable getFailure() {
                        return deserializedFailure;
                    }
                };
            } else if (resultEvent.type == PhasedBuildActionResult.Type.AFTER_BUILD) {
                phasedActionResult = new AfterBuildResult<Object>() {
                    @Nullable
                    @Override
                    public Object getResult() {
                        return deserializedResult;
                    }

                    @Nullable
                    @Override
                    public Throwable getFailure() {
                        return deserializedFailure;
                    }
                };
            }
            phasedActionResultListener.onResult(phasedActionResult);
        } else {
            delegate.dispatch(event);
        }
    }
}
