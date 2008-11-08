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
package org.gradle.initialization;

import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.hamcrest.Description;
import org.gradle.api.Project;

/**
 * @author Hans Dockter
 */
public class BuildListenerAction implements Action {
    private Project rootProject;

    public BuildListenerAction(Project rootProject) {
        this.rootProject = rootProject;
    }

    public void describeTo(Description description) {
        description.appendText("build listener");
    }

    public Object invoke(Invocation invocation) throws Throwable {
        BuildSourceBuilder.BuildSourceBuildListener buildListener = (BuildSourceBuilder.BuildSourceBuildListener)
                invocation.getParameter(0);
        buildListener.setRootProject(rootProject);
        return null;
    }
}
