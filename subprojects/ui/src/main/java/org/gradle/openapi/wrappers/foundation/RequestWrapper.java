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

import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.openapi.external.foundation.RequestVersion1;

/**
 * Implementation of RequestVersion1 meant to help shield external users from internal changes.
 */
public class RequestWrapper implements RequestVersion1 {
    private Request request;

    public RequestWrapper(Request request) {
        this.request = request;
    }

    /**
     * @return the full gradle command line of this request
     */
    public String getFullCommandLine() {
        return request.getFullCommandLine();
    }

    /**
     * @return the display name of this request. Often this is the same as the full command line, but favorites may specify something more user-friendly.
     */
    public String getDisplayName() {
        return request.getDisplayName();
    }

    /**
     * @return whether or not output should always be shown. If false, only show it when errors occur.
     */
    public boolean forceOutputToBeShown() {
        return request.forceOutputToBeShown();
    }

    /**
     * Cancels this request.
     */
    public boolean cancel() {
        return request.cancel();
    }

    @Override
    public int hashCode() {
        return request.hashCode();
    }

    @Override
    public boolean equals(Object otherObject) {
        if (!(otherObject instanceof RequestWrapper)) {
            return false;
        }

        return ((RequestWrapper) otherObject).request.equals(request);
    }

    /**
     * @return the type of the request. Either EXECUTION or REFRESH
     */
    public String getType() {
        if (request.getType() == ExecutionRequest.TYPE) {
            return EXECUTION_TYPE;
        }

        if (request.getType() == RefreshTaskListRequest.TYPE) {
            return REFRESH_TYPE;
        }

        return UNKNOWN_TYPE_PREFIX + request.getType();
    }

    @Override
    public String toString() {
        return request.toString();
    }
}
