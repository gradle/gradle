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

package org.gradle.internal.buildtree;

import com.google.common.collect.ImmutableList;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.problems.buildtree.ProblemReporter;
import org.gradle.problems.buildtree.ProblemReporter.ProblemConsumer;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProblemReportingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildLayout buildLayout;
    private final List<? extends ProblemReporter> reporters;

    public ProblemReportingBuildActionRunner(BuildActionRunner delegate, ExceptionAnalyser exceptionAnalyser, BuildLayout buildLayout, List<? extends ProblemReporter> reporters) {
        this.delegate = delegate;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildLayout = buildLayout;
        this.reporters = ImmutableList.sortedCopyOf(Comparator.comparing(ProblemReporter::getId), reporters);
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        RootProjectBuildDirCollectingListener rootProjectBuildDirListener = getRootProjectBuildDirCollectingListener(buildController);
        Result result = delegate.run(action, buildController);

        File rootProjectBuildDir = rootProjectBuildDirListener.rootProjectBuildDir;
        List<Throwable> failures = reportProblems(rootProjectBuildDir);
        return result.addFailures(failures);
    }

    private List<Throwable> reportProblems(File rootProjectBuildDir) {
        List<Throwable> failures = new ArrayList<>();
        ProblemConsumer collector = failure -> failures.add(exceptionAnalyser.transform(failure));
        for (ProblemReporter reporter : reporters) {
            try {
                reporter.report(rootProjectBuildDir, collector);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        return failures;
    }

    private RootProjectBuildDirCollectingListener getRootProjectBuildDirCollectingListener(BuildTreeLifecycleController buildController) {
        RootProjectBuildDirCollectingListener listener = new RootProjectBuildDirCollectingListener(
            defaultRootBuildDirOf()
        );
        buildController.beforeBuild(gradle -> gradle.addBuildListener(listener));
        return listener;
    }

    private File defaultRootBuildDirOf() {
        return new File(buildLayout.getRootDirectory(), "build");
    }

    private static class RootProjectBuildDirCollectingListener extends InternalBuildAdapter {
        File rootProjectBuildDir;

        public RootProjectBuildDirCollectingListener(File defaultBuildDir) {
            this.rootProjectBuildDir = defaultBuildDir;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            rootProjectBuildDir = gradle.getRootProject().getLayout().getBuildDirectory().getAsFile().get();
        }
    }
}
