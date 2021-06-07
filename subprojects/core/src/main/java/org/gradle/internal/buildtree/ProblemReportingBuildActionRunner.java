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
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.problems.buildtree.ProblemReporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ProblemReportingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final ExceptionAnalyser exceptionAnalyser;
    private final List<? extends ProblemReporter> reporters;

    public ProblemReportingBuildActionRunner(BuildActionRunner delegate, ExceptionAnalyser exceptionAnalyser, List<? extends ProblemReporter> reporters) {
        this.delegate = delegate;
        this.exceptionAnalyser = exceptionAnalyser;
        this.reporters = ImmutableList.sortedCopyOf(Comparator.comparing(ProblemReporter::getId), reporters);
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        File defaultRootBuildDir = new File(buildController.getGradle().getServices().get(BuildLayout.class).getRootDirectory(), "build");
        RootProjectBuildDirCollectingListener listener = new RootProjectBuildDirCollectingListener(defaultRootBuildDir);
        buildController.getGradle().addBuildListener(listener);

        Result result = delegate.run(action, buildController);

        List<Throwable> failures = new ArrayList<>();
        File rootProjectBuildDir = listener.buildDir;
        Consumer<Throwable> collector = failure -> failures.add(exceptionAnalyser.transform(failure));
        for (ProblemReporter reporter : reporters) {
            try {
                reporter.report(rootProjectBuildDir, collector);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        return result.addFailures(failures);
    }

    private static class RootProjectBuildDirCollectingListener extends InternalBuildAdapter {
        File buildDir;

        public RootProjectBuildDirCollectingListener(File defaultBuildDir) {
            this.buildDir = defaultBuildDir;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            buildDir = gradle.getRootProject().getBuildDir();
        }
    }
}
