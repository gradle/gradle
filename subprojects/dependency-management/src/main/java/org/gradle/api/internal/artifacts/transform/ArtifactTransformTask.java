/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

@NonNullApi
public abstract class ArtifactTransformTask extends DefaultTask {

    private final ArtifactTransformer transform;
    private TransformationResult transformationResult;
    private final WorkerLeaseService workerLeaseService;

    @Inject
    public ArtifactTransformTask(UserCodeBackedTransformer transform, WorkerLeaseService workerLeaseService) {
        this.transform = transform;
        this.workerLeaseService = workerLeaseService;
    }

    @Internal
    public TransformationResult getTransformationResult() {
        return transformationResult;
    }

    public abstract TransformationResult incomingTransformationResult();

    @TaskAction
    public void transformArtifacts() {
        workerLeaseService.withoutProjectLock(new Runnable() {
            @Override
            public void run() {
                TransformationResult incoming = incomingTransformationResult();
                transformationResult = transform(incoming);
            }
        });
    }

    private TransformationResult transform(File file) {
        try {
            List<File> result = transform.transform(file);
            return new TransformationResult(result);
        } catch (Throwable e) {
            return new TransformationResult(e);
        }
    }

    private TransformationResult transform(TransformationResult incoming) {
        if (incoming.isFailed()) {
            return incoming;
        }
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (File file : incoming.getResult()) {
            TransformationResult transformationResult = transform(file);
            if (transformationResult.isFailed()) {
                return transformationResult;
            }
            builder.addAll(transformationResult.getResult());
        }
        return new TransformationResult(builder.build());
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExecuter() {
        throw new UnsupportedOperationException();
    }

    public static class TransformationResult implements ArtifactTransformationResult {
        private final List<File> transformedFiles;
        private Throwable failure;

        public TransformationResult(List<File> transformedFiles) {
            this.transformedFiles = transformedFiles;
            this.failure = null;
        }

        public TransformationResult(Throwable failure) {
            this.transformedFiles = null;
            this.failure = failure;
        }

        public boolean isFailed() {
            return failure != null;
        }

        @Override
        public List<File> getResult() {
            return Preconditions.checkNotNull(transformedFiles);
        }

        @Override
        public Throwable getFailure() {
            return failure;
        }
    }
}
