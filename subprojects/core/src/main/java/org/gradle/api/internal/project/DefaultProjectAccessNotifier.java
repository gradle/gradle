/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.initialization.ProjectAccessListener;
import org.gradle.initialization.ProjectAccessNotifier;

import java.util.List;


public class DefaultProjectAccessNotifier implements ProjectAccessNotifier {

    private final ProjectAccessListener listener;

    public DefaultProjectAccessNotifier(List<ProjectAccessListener> listeners) {
        listener = new CompositeProjectAccessListener(listeners);
    }

    @Override
    public ProjectAccessListener getListener() {
        return listener;
    }

    private static class CompositeProjectAccessListener implements ProjectAccessListener {

        private final List<ProjectAccessListener> listeners;

        private CompositeProjectAccessListener(List<ProjectAccessListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public void beforeRequestingTaskByPath(ProjectInternal targetProject) {
            listeners.forEach(listener -> listener.beforeRequestingTaskByPath(targetProject));
        }

        @Override
        public void beforeResolvingProjectDependency(ProjectInternal dependencyProject) {
            listeners.forEach(listener -> listener.beforeResolvingProjectDependency(dependencyProject));
        }

        @Override
        public void onProjectAccess(String invocationDescription, Object invocationSource) {
            listeners.forEach(listener -> listener.onProjectAccess(invocationDescription, invocationSource));
        }
    }
}
