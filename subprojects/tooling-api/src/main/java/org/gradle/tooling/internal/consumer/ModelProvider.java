/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.tooling.internal.build.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalBuildEnvironment;

/**
 * by Szczepan Faber, created at: 12/21/11
 */
public class ModelProvider {

    public <T> T provide(ConsumerConnection connection, Class<T> type, ConsumerOperationParameters operationParameters) {
        VersionDetails version = connection.getMetaData().getVersionDetails();
        if (type == InternalBuildEnvironment.class && !version.supportsCompleteBuildEnvironment()) {
            //early versions of provider do not support BuildEnvironment model
            //since we know the gradle version at least we can give back some result
            VersionOnlyBuildEnvironment out = new VersionOnlyBuildEnvironment(version.getVersion());
            return type.cast(out);
        }
        if (version.clientHangsOnEarlyDaemonFailure()) {
            //those version require special handling because of the client hanging bug
            //it is due to the exception handing on the daemon side in M5 and M6
            if (version.isPostM6Model(type)) {
                String message = String.format("I don't know how to build a model of type '%s'.", type.getSimpleName());
                throw new UnsupportedOperationException(message);
            }
        }
        return connection.getModel(type, operationParameters);
    }
}
