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
package org.gradle.openapi.external.ui;

/**
 * <p>This interface informs you when the output pane is displaying requests. This is NOT for general output of gradle commands.
 *
 * <p>This is a mirror of OutputUILord.OutputObserver inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 *
 * @deprecated No replacement
 */
@Deprecated
public interface OutputObserverVersion1 {
    /**
     * Notification that a request was added to the output. This means we've got some output that is useful to display. <!      Name             Description>
     *
     * @param requestID an ID you can use to identify this request when it is complete.
     * @param fullCommandLine the command line for the request that was added
     * @param displayName the display name of this command (often the same as the full command line)
     * @param forceOutputToBeShown true if this request wants to force its output to be shown
     */
    public void executionRequestAdded(long requestID, String fullCommandLine, String displayName, boolean forceOutputToBeShown);

    /**
     * Notification that a refresh task list request was added to the output. This means we've got some output that is useful to display.
     *
     * @param requestID an ID you can use to identify this request when it is complete.
     * @param forceOutputToBeShown true if this request wants to force its output to be shown
     */
    public void refreshRequestAdded(long requestID, boolean forceOutputToBeShown);

    /**
     * Notification that a request is complete. Note: if its canceled, you'll just get an outputTabClosed notification.
     *
     * @param requestID the ID of the request that is complete. It is given to you in executionRequestAdded or refreshRequestAdded.
     * @param wasSuccessful true if was successful, false if not or was cancelled.
     */
    public void requestComplete(long requestID, boolean wasSuccessful);

    /**
     * Notification that an output tab was closed, possibly because it was canceled. You might want to know this if you want to close your IDE output window when all tabs are closed.
     *
     * @param requestID the ID of the request associated with this tab. It is given to you in executionRequestAdded or refreshRequestAdded.
     */
    public void outputTabClosed(long requestID);
}
