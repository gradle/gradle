/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

class DefaultBuildActionExecuter<T> extends AbstractLongRunningOperation<DefaultBuildActionExecuter<T>> implements BuildActionExecuter<T> {
    private final BuildAction<T> buildAction;

    public DefaultBuildActionExecuter(BuildAction<T> buildAction, ConnectionParameters parameters) {
        super(new ConsumerOperationParameters(parameters));
        this.buildAction = buildAction;
    }

    @Override
    protected DefaultBuildActionExecuter<T> getThis() {
        return this;
    }

    public T run() throws GradleConnectionException {
        return buildAction.execute(new BuildController() {
        });
    }

    public void run(ResultHandler<? super T> handler) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }
}
