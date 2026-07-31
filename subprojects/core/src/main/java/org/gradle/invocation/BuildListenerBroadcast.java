/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.invocation;

import org.gradle.BuildListener;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;

public class BuildListenerBroadcast {

    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;

    public BuildListenerBroadcast(ListenerManager listenerManager) {
        this.buildListenerBroadcast = listenerManager.createAnonymousBroadcaster(BuildListener.class);
    }


    public ListenerBroadcast<BuildListener> getBroadcast() {
        return buildListenerBroadcast;
    }

    public void resetState() {
        buildListenerBroadcast.removeAll();
    }

    public BuildListener getSource() {
        return buildListenerBroadcast.getSource();
    }
}
