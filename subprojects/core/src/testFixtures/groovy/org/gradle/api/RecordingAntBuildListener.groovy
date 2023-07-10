/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.apache.tools.ant.BuildEvent
import org.apache.tools.ant.BuildListener

class RecordingAntBuildListener implements BuildListener {

    List<BuildEvent> buildStarted = []
    List<BuildEvent> buildFinished = []
    List<BuildEvent> targetStarted = []
    List<BuildEvent> targetFinished = []
    List<BuildEvent> taskStarted = []
    List<BuildEvent> taskFinished = []
    List<BuildEvent> messageLogged = []

    void buildStarted(BuildEvent event) {
        buildStarted << event
    }

    void buildFinished(BuildEvent event) {
        buildFinished << event
    }

    void targetStarted(BuildEvent event) {
        targetStarted << event
    }

    void targetFinished(BuildEvent event) {
        targetFinished << event
    }

    void taskStarted(BuildEvent event) {
        taskStarted << event
    }

    void taskFinished(BuildEvent event) {
        taskFinished << event
    }

    void messageLogged(BuildEvent event) {
        messageLogged << event
    }

}
