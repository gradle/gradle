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

package org.gradle.tooling.internal.consumer;

import org.gradle.api.Transformer;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.protocol.AfterBuildResult;
import org.gradle.tooling.internal.protocol.AfterConfigurationResult;
import org.gradle.tooling.internal.protocol.AfterLoadingResult;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;

import javax.annotation.Nullable;

/**
 * Adapts individual result handlers of actions in a {@link PhasedBuildAction} to a unified listener to be provided to the connection.
 */
public class DefaultPhasedActionResultListener implements PhasedActionResultListener {
    @Nullable private final ResultHandlerVersion1<?> afterLoadingResultHandler;
    @Nullable private final ResultHandlerVersion1<?> afterConfigurationResultHandler;
    @Nullable private final ResultHandlerVersion1<?> afterBuildResultHandler;

    public DefaultPhasedActionResultListener(@Nullable ResultHandler<?> afterLoadingResultHandler,
                                             @Nullable ResultHandler<?> afterConfigurationResultHandler,
                                             @Nullable ResultHandler<?> afterBuildResultHandler) {
        this.afterLoadingResultHandler = adaptHandler(afterLoadingResultHandler);
        this.afterConfigurationResultHandler = adaptHandler(afterConfigurationResultHandler);
        this.afterBuildResultHandler = adaptHandler(afterBuildResultHandler);
    }

    @Override
    public void onResult(PhasedActionResult<?> result) {
        Object model = result.getResult();
        Throwable failure = result.getFailure();
        if (result instanceof AfterLoadingResult) {
            onComplete(model, failure, afterLoadingResultHandler);
        } else if (result instanceof AfterConfigurationResult) {
            onComplete(model, failure, afterConfigurationResultHandler);
        } else if (result instanceof AfterBuildResult) {
            onComplete(model, failure, afterBuildResultHandler);
        }
    }

    private <T> void onComplete(@Nullable Object result, @Nullable Throwable failure, @Nullable ResultHandlerVersion1<T> handler) {
        if (handler != null) {
            if (failure == null) {
                handler.onComplete((T) result);
            } else {
                handler.onFailure(failure);
            }
        }
    }

    @Nullable
    private static <T> ResultHandlerVersion1<T> adaptHandler(@Nullable ResultHandler<T> handler) {
        return handler == null ? null : new ResultHandlerAdapter<T>(handler, new ExceptionTransformer(new Transformer<String, Throwable>() {
            @Override
            public String transform(Throwable throwable) {
                return "Could not execute build action in phased action build.";
            }
        }) {
            @Override
            public GradleConnectionException transform(Throwable failure) {
                if (failure instanceof InternalBuildActionFailureException) {
                    return new BuildActionFailureException("The supplied build action failed with an exception.", failure.getCause());
                }
                return super.transform(failure);
            }
        });
    }
}
