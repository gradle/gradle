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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.util.List;

public class NotifyAboutChangedPathsConsumerAction implements ConsumerAction<Void> {

    private final ConsumerOperationParameters.Builder operationParamsBuilder;
    private final List<String> absolutePaths;

    NotifyAboutChangedPathsConsumerAction(ConsumerOperationParameters.Builder operationParamsBuilder, List<String> absolutePaths) {
        this.operationParamsBuilder = operationParamsBuilder;
        this.absolutePaths = absolutePaths;
    }

    @Override
    public ConsumerOperationParameters getParameters() {
        return operationParamsBuilder.build();
    }

    @Override
    public Void run(ConsumerConnection connection) {
        connection.notifyDaemonsAboutChangedPaths(absolutePaths, getParameters());
        return null;
    }
}
