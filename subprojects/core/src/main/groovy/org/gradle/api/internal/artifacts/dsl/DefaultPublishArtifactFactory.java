/*
 * Copyright 2009 the original author or authors.
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
 
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.InvalidUserDataException;

/**
 * @author Hans Dockter
 */
public class DefaultPublishArtifactFactory implements PublishArtifactFactory {
    public PublishArtifact createArtifact(Object notation) {
        if (notation instanceof AbstractArchiveTask) {
            return new ArchivePublishArtifact((AbstractArchiveTask) notation);
        }
        throw new InvalidUserDataException("Notation is invalid for an artifact! Passed notation=" + notation);
    }                            
}
