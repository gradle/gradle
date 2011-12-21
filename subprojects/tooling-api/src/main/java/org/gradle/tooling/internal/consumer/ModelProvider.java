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

import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ProjectVersion3;

/**
 * by Szczepan Faber, created at: 12/21/11
 */
public class ModelProvider {
    public ProjectVersion3 provide(ConnectionVersion4 connection, Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        String ver = connection.getMetaData().getVersion();
        if ("1.0-milestone-5".equals(ver) || "1.0-milestone-6".equals(ver)) {
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
