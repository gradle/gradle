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

import org.gradle.tooling.internal.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildEnvironment;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.util.GradleVersion;

/**
 * by Szczepan Faber, created at: 12/21/11
 */
public class ModelProvider {

    private final static GradleVersion M5 = GradleVersion.version("1.0-milestone-5");
    private final static GradleVersion M6 = GradleVersion.version("1.0-milestone-6");

    public ProjectVersion3 provide(ConnectionVersion4 connection, Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        GradleVersion version = GradleVersion.version(connection.getMetaData().getVersion());
        if (type == InternalBuildEnvironment.class && version.compareTo(M6) <= 0) {
            //early versions of provider do not support BuildEnvironment model
            //since we know the gradle version at least we can give back some result
            return new VersionOnlyBuildEnvironment(connection.getMetaData().getVersion());
        }
        if (version.equals(M5) || version.equals(M6)) {
            //those version require special handling because of the client hanging bug
            //it is due to the exception handing on the daemon side in M5 and M6

            if (DefaultProjectConnection.getModelsPostM6().containsValue(type)) {
                String message = String.format("I don't know how to build a model of type '%s'.", type.getSimpleName());
                throw new UnsupportedOperationException(message);
            }
        }
        return connection.getModel(type, operationParameters);
    }
}
