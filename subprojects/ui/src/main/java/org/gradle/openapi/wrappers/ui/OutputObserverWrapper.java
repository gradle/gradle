/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.swing.generic.OutputUILord;
import org.gradle.openapi.external.ui.OutputObserverVersion1;

/**
 * Wrapper to shield version changes in OutputUILord.OutputObserver from an external user of the gradle open API.
 */
public class OutputObserverWrapper implements OutputUILord.OutputObserver {
    private OutputObserverVersion1 outputObserverVersion1;

    public OutputObserverWrapper(OutputObserverVersion1 outputObserverVersion1) {
        this.outputObserverVersion1 = outputObserverVersion1;

        //when future versions are added, doing the following and then delegating
        //the new functions to OutputObserverVersion2 keeps things compatible.
        //if( outputObserverVersion1 instanceof OutputObserverVersion2 )
        //   outputObserverVersion2 = (OutputObserverVersion2) outputObserverVersion1;
    }

    /**
     * Notification that a request was added to the output. This means we've got some output that is useful to display.
     *
     * Note: this is slightly different from the GradlePluginLord.RequestObserver. While these are directly related, this one really means that it has been added to the UI. <!      Name
     * Description>
     *
     * @param request the request that was added.
     */
    public void executionRequestAdded(ExecutionRequest request) {
        //Note: I don't like passing the request itself here, but a user might need an ID and trying to
        //map the requests to something else when they can live for an indeterminate amount of time was too complex.
        //I considered using a straight unique ID
        outputObserverVersion1.executionRequestAdded(request.getRequestID(), request.getFullCommandLine(), request.getDisplayName(), request.forceOutputToBeShown());
    }

    /**
     * Notification that a refresh task list request was added to the output. This means we've got some output that is useful to display.
     *
     * Note: this is slightly different from the GradlePluginLord.RequestObserver. While these are directly related, this one really means that it has been added to the UI. <!      Name
     * Description>
     *
     * @param request the request that was added.
     */
    public void refreshRequestAdded(RefreshTaskListRequest request) {
        outputObserverVersion1.refreshRequestAdded(request.getRequestID(), request.forceOutputToBeShown());
    }

    /**
     * Notification that an output tab was closed. You might want to know this if you want to close your IDE output window when all tabs are closed
     *
     * @param remainingTabCount the number of open tabs
     */
    public void outputTabClosed(Request request) {
        outputObserverVersion1.outputTabClosed(request.getRequestID());
    }

    /**
     * Notification that execution of a request is complete
     *
     * @param request the original request
     */
    public void reportExecuteFinished(Request request, boolean wasSuccessful) {
        outputObserverVersion1.requestComplete(request.getRequestID(), wasSuccessful);
    }
}
