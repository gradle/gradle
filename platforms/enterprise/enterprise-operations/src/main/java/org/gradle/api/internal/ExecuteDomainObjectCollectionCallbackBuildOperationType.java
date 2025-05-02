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

package org.gradle.api.internal;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Fired when a domain object collection executes a registered callback that was registered by user code.
 *
 * @since 5.1
 */
public final class ExecuteDomainObjectCollectionCallbackBuildOperationType implements BuildOperationType<ExecuteDomainObjectCollectionCallbackBuildOperationType.Details, ExecuteDomainObjectCollectionCallbackBuildOperationType.Result> {

    public interface Details {

        /**
         * The application ID of the script or plugin that registered the listener.
         *
         * @see org.gradle.api.internal.plugins.ApplyPluginBuildOperationType.Details#getApplicationId()
         * @see org.gradle.configuration.ApplyScriptPluginBuildOperationType.Details#getApplicationId()
         */
        long getApplicationId();

    }

    public interface Result {
    }

    static final ExecuteDomainObjectCollectionCallbackBuildOperationType.Result RESULT = new ExecuteDomainObjectCollectionCallbackBuildOperationType.Result() {
    };

    private ExecuteDomainObjectCollectionCallbackBuildOperationType() {
    }
}

