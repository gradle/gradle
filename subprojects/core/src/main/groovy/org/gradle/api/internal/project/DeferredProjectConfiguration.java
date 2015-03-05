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

import com.google.common.collect.Lists;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Project;

import java.util.List;

@NotThreadSafe
public class DeferredProjectConfiguration {

    private final Project project;
    private final List<Runnable> configuration = Lists.newLinkedList();
    private boolean fired;

    public DeferredProjectConfiguration(Project project) {
        this.project = project;
    }

    public void add(Runnable configuration) {
        if (fired) {
            throw new IllegalStateException("Cannot add deferred configuration for project " + project.getPath());
        } else {
            this.configuration.add(configuration);
        }
    }

    public void fire() {
        if (!fired) {
            fired = true;
            for (Runnable runnable : configuration) {
                runnable.run();
            }
        }
    }
    
}
