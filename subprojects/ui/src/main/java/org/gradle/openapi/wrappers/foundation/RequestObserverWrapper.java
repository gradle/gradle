/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.wrappers.foundation;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.openapi.external.foundation.RequestObserverVersion1;

/**
 * * Implementation of RequestObserverVersion1 meant to help shield external users from internal changes.
 */
public class RequestObserverWrapper implements GradlePluginLord.RequestObserver {

    private RequestObserverVersion1 requestObserver;

    public RequestObserverWrapper(RequestObserverVersion1 requestObserver) {
        this.requestObserver = requestObserver;
    }

    public void executionRequestAdded(ExecutionRequest request) {
        requestObserver.executionRequestAdded(new RequestWrapper(request));
    }

    public void refreshRequestAdded(RefreshTaskListRequest request) {
        requestObserver.refreshRequestAdded(new RequestWrapper(request));
    }

    /**
     * Notification that a command is about to be executed. This is mostly useful for IDE's that may need to save their files.
     */
    public void aboutToExecuteRequest(Request request) {
        requestObserver.aboutToExecuteRequest(new RequestWrapper(request));
    }

    /**
     * Notification that the command has completed execution.
     *
     * @param request the original request containing the command that was executed
     * @param result the result of the command
     * @param output the output from gradle executing the command
     */
    public void requestExecutionComplete(Request request, int result, String output) {
        requestObserver.requestExecutionComplete(new RequestWrapper(request), result, output);
    }

    @Override
    public int hashCode() {
        return requestObserver.hashCode();
    }

    @Override
    public boolean equals(Object otherObject) {
        if (!(otherObject instanceof RequestObserverWrapper)) {
            return false;
        }

        return ((RequestObserverWrapper) otherObject).requestObserver.equals(requestObserver);
    }
}
