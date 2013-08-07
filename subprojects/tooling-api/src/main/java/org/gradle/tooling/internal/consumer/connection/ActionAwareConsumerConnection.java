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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.ClientBuildAction;
import org.gradle.tooling.internal.protocol.ClientBuildActionExecutor;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;

public class ActionAwareConsumerConnection extends ModelBuilderBackedConsumerConnection {
    private final ClientBuildActionExecutor executor;

    public ActionAwareConsumerConnection(ConnectionVersion4 delegate, ProtocolToModelAdapter adapter) {
        super(delegate, adapter);
        executor = (ClientBuildActionExecutor) delegate;
    }

    @Override
    public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return executor.run(new BuildActionAdapter<T>(action), operationParameters).getModel();
    }

    private static class BuildActionAdapter<T> implements ClientBuildAction<T> {
        private final BuildAction<T> action;

        public BuildActionAdapter(BuildAction<T> action) {
            this.action = action;
        }

        public T execute() {
            return action.execute(new BuildController() {
            });
        }
    }
}
