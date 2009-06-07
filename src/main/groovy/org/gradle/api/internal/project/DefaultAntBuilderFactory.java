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
import org.gradle.api.Project;

/**
 * @author Hans Dockter
 */
public class DefaultAntBuilderFactory implements AntBuilderFactory {
    private final BuildListener buildListener;
    private final Project project;

    public DefaultAntBuilderFactory(BuildListener buildListener, Project project) {
        this.buildListener = buildListener;
        this.project = project;
    }

    public groovy.util.AntBuilder createAntBuilder() {
        AntBuilder antBuilder = new AntBuilder();
        antBuilder.getProject().setBaseDir(project.getProjectDir());
        antBuilder.getProject().removeBuildListener((BuildListener) antBuilder.getProject().getBuildListeners().get(0));
        antBuilder.getProject().addBuildListener(buildListener);
        return antBuilder;
    }
}
