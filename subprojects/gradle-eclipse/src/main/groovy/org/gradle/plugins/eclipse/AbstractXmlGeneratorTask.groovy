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
package org.gradle.plugins.eclipse

import org.gradle.api.Action
import org.gradle.api.internal.ConventionTask
import org.gradle.listener.ListenerBroadcast

/**
 * @author Hans Dockter
 */
class AbstractXmlGeneratorTask extends ConventionTask {
    ListenerBroadcast<Action> beforeConfiguredActions = new ListenerBroadcast<Action>(Action.class);
    ListenerBroadcast<Action> whenConfiguredActions = new ListenerBroadcast<Action>(Action.class);

    void beforeConfigured(Closure closure) {
        beforeConfiguredActions.add("execute", closure);
    }

    void whenConfigured(Closure closure) {
        whenConfiguredActions.add("execute", closure);
    }


}
