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
package org.gradle.api;

/**
 * A listener to be registered with a project. Its prupose is to inform about the execution of the build script
 * belonging to the project.
 *
 * @author Hans Dockter
 *
 * @see org.gradle.api.Project#addAfterEvaluateListener(AfterEvaluateListener) 
 */
public interface AfterEvaluateListener {
    /**
     * This method is called by the Project observable after its build script has been run.
     *
     * @param project An instance of the project where the AfterEvaluateListener is registered.
     */
    void afterEvaluate(Project project);
}
