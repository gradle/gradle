/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.FileDetails;
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IncrementalFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy;
import org.gradle.api.tasks.Classpath;

import java.lang.annotation.Annotation;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.getRelativeSnapshot;

public class ClasspathPropertyAnnotationHandler implements PropertyAnnotationHandler {
    static final SnapshotNormalizationStrategy STRATEGY = new SnapshotNormalizationStrategy() {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
            // Ignore path of root files and directories
            if (fileDetails.isRoot()) {
                return new IgnoredPathFileSnapshot(snapshot);
            }
            return getRelativeSnapshot(fileDetails, snapshot, stringInterner);
        }
    };

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Classpath.class;
    }

    @Override
    public void attachActions(final TaskPropertyActionContext context) {
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, Callable<Object> futureValue) {
                task.getInputs().files(futureValue)
                    .withPropertyName(context.getName())
                    .orderSensitive(true)
                    .withSnapshotNormalizationStrategy(STRATEGY);
            }
        });
    }
}
