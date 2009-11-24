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
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;

/**
 This interface manages the output of executing gradle tasks.
 */
public interface OutputUILord
{
   void setOnlyShowOutputOnErrors( boolean show );

   boolean getOnlyShowOutputOnErrors();


         public interface OutputObserver
         {
            /**
               Notification that a request was added to the output. This means we've got some output
               that is useful to display.

               Note: this is slightly different from the GradlePluginLord.RequestObserver. While
               these are directly related, this one really means that it has been added to the UI.
               <!      Name            Description>
               @param  request         the request that was added.
            */
            void executionRequestAdded( ExecutionRequest request );

            /**
              Notification that a refresh task list request was added to the output. This means
              we've got some output that is useful to display.

              Note: this is slightly different from the GradlePluginLord.RequestObserver. While
              these are directly related, this one really means that it has been added to the UI.
               <!      Name            Description>
               @param  request         the request that was added.
             */
            void refreshRequestAdded( RefreshTaskListRequest request );

            /**
             Notification that an output tab was closed. You might want to know this if you want to close your
             IDE output window when all tabs are closed
             */
            public void outputTabClosed( Request request );

            /**
             Notification that execution of a request is complete
             @param request the original request
             @param wasSuccessful
             */
            public void reportExecuteFinished( Request request, boolean wasSuccessful );
         }

   public void addOutputObserver( OutputObserver observer, boolean inEventQueue );
   public void removeOutputObserver( OutputObserver observer );

   public int getTabCount();
}
