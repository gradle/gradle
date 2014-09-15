/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.internal.Exceptions;

class UnsupportedActionRunner implements ActionRunner {
    private final VersionDetails versionDetails;

    UnsupportedActionRunner(VersionDetails versionDetails) {
        this.versionDetails = versionDetails;
    }

    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature("execution of build actions provided by the tooling API client", versionDetails.getVersion(), "1.8");
    }
}
