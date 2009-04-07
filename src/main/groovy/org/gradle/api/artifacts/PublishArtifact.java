/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Date;

/**
 * <p>A {@code PublishArtifact} is an artifact produced by a project.</p>
 *
 * @author Hans Dockter
 */
public interface PublishArtifact {
    String getName();

    String getExtension();

    String getType();

    String getClassifier();
    
    TaskDependency getTaskDependency();

    File getFile();

    Date getDate();
}
