/*
 * Copyright 2007-2008 the original author or authors.
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

import org.apache.tools.ant.BuildListener;
import org.gradle.api.AntBuilder;
import org.gradle.api.Project;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;

public class DefaultAntBuilderFactory implements Factory<AntBuilder> {
    private final BuildListener buildListener;
    private final Project project;
    private final CompositeStoppable stoppable = new CompositeStoppable();

    public DefaultAntBuilderFactory(BuildListener buildListener, Project project) {
        this.buildListener = buildListener;
        this.project = project;
    }

    public DefaultAntBuilder create() {
        DefaultAntBuilder antBuilder = new DefaultAntBuilder(project);
        antBuilder.getProject().setBaseDir(project.getProjectDir());
        antBuilder.getProject().removeBuildListener(antBuilder.getProject().getBuildListeners().get(0));
        antBuilder.getProject().addBuildListener(buildListener);
        stoppable.add(antBuilder);
        return antBuilder;
    }

    public void close() {
        stoppable.stop();
    }
}
