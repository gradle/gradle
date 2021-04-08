/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CurrentCompilationAccess {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentCompilationAccess.class);
    private final ClassSetAnalyzer classSetAnalyzer;
    private final BuildOperationExecutor buildOperationExecutor;
    private ClassSetAnalysisData classpathSnapshot;

    public CurrentCompilationAccess(ClassSetAnalyzer classSetAnalyzer, BuildOperationExecutor buildOperationExecutor) {
        this.classSetAnalyzer = classSetAnalyzer;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public ClassSetAnalysisData analyzeOutputFolder(File outputFolder) {
        Timer clock = Time.startTimer();
        ClassSetAnalysisData snapshot = classSetAnalyzer.analyzeOutputFolder(outputFolder);
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
        return snapshot;
    }


    public ClassSetAnalysisData getClasspathSnapshot(final Iterable<File> entries) {
        if (classpathSnapshot == null) {
            Timer clock = Time.startTimer();
            classpathSnapshot = ClassSetAnalysisData.merge(doSnapshot(entries));
            LOG.info("Created classpath snapshot for incremental compilation in {}.", clock.getElapsed());
        }
        return classpathSnapshot;
    }

    private List<ClassSetAnalysisData> doSnapshot(Iterable<File> entries) {
        return snapshotAll(entries).stream()
            .map(CreateSnapshot::getSnapshot)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<CreateSnapshot> snapshotAll(final Iterable<File> entries) {
        final List<CreateSnapshot> snapshotOperations = new ArrayList<>();

        buildOperationExecutor.runAll((Action<BuildOperationQueue<CreateSnapshot>>) buildOperationQueue -> {
            for (File entry : entries) {
                CreateSnapshot operation = new CreateSnapshot(entry);
                snapshotOperations.add(operation);
                buildOperationQueue.add(operation);
            }
        });
        return snapshotOperations;
    }

    private class CreateSnapshot implements RunnableBuildOperation {
        private final File entry;
        private ClassSetAnalysisData snapshot;

        private CreateSnapshot(File entry) {
            this.entry = entry;
        }

        @Override
        public void run(BuildOperationContext context) {
            if (entry.exists()) {
                snapshot = classSetAnalyzer.analyzeClasspathEntry(entry);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Create incremental compile snapshot for " + entry);
        }

        @Nullable
        public ClassSetAnalysisData getSnapshot() {
            return snapshot;
        }
    }
}
