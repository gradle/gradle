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
package org.gradle.openapi.external.foundation;

/**
 * This allows you to observer when Gradle commands are executed/complete. It is an abstraction of a GradlePluginLord.RequestObserver.
 *
 * <p>This is a mirror of GradlePluginLord.RequestObserver inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface RequestObserverVersion1 {

    /**
     * Notification that an execution request was added to the queue. This is the normal request that initiates a gradle command.
     *
     * @param request the request that was added
     */
    public void executionRequestAdded(RequestVersion1 request);

    /**
     * Notification that a refresh request was added to the queue. This type of request updates the task tree.
     */
    public void refreshRequestAdded(RequestVersion1 request);

    /**
     * Notification that a command is about to be executed. This is mostly useful for IDE's that may need to save their files. This is always called after a request has been added to the queue.
     */
    public void aboutToExecuteRequest(RequestVersion1 request);

    /**
     * Notification that a request has completed execution.
     *
     * @param request the original request containing the command that was executed
     * @param result the result of the command
     * @param output the output from gradle executing the command
     */
    public void requestExecutionComplete(RequestVersion1 request, int result, String output);
}
