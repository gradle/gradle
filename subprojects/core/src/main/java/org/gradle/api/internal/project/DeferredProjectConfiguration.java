/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.LinkedList;
import java.util.List;

@ServiceScope(Scope.Project.class)
@NotThreadSafe
public class DeferredProjectConfiguration {

    private final static String TRACE = "org.gradle.trace.deferred.project.configuration";

    private final Project project;
    private final List<Runnable> configuration = new LinkedList<>();
    private boolean fired;

    private Throwable firedSentinel;

    public DeferredProjectConfiguration(Project project) {
        this.project = project;
    }

    public void add(Runnable configuration) {
        if (fired) {
            String message = "Cannot add deferred configuration for project " + project.getPath();
            if (firedSentinel == null) {
                throw new IllegalStateException(message);
            } else {
                throw new IllegalStateException(message, firedSentinel);
            }
        } else {
            this.configuration.add(configuration);
        }
    }

    public void fire() {
        if (!fired) {
            if (Boolean.getBoolean(TRACE)) {
                firedSentinel = new Exception("Project '" + project.getPath() + "' deferred configuration fired");
            }
            fired = true;
            try {
                for (Runnable runnable : configuration) {
                    runnable.run();
                }
            } finally {
                configuration.clear();
            }
        }
    }

}
