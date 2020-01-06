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

package org.gradle.performance.fixture;

import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.util.List;

public abstract class Profiler {
    private static final String TARGET_DIR_KEY = "org.gradle.performance.flameGraphTargetDir";

    public static Profiler create() {
        String targetDir = getJfrProfileTargetDir();
        if (targetDir != null && !Jvm.current().isIbmJvm()) {
            return new JfrProfiler(new File(targetDir));
        } else {
            return new NoopProfiler();
        }
    }

    public static String getJfrProfileTargetDir() {
        return System.getProperty(TARGET_DIR_KEY);
    }

    public abstract List<String> getAdditionalJvmOpts(BuildExperimentSpec spec);

    /**
     * The {@literal ;} separated list of jvm arguments to start a recording.
     *
     * This can be passed to the {@link org.gradle.performance.generator.JavaTestProject}s to enable recordings for the compiler daemon via:
     * <pre>
     * runner.args += ["-PjavaCompileJvmArgs=${Profiler.create().getJvmOptsForUseInBuild("compiler-daemon")}"]
     * </pre>
     * @param recordingsDirectoryRelativePath The directory where the profiler recordings will be generated.
     *                                        Relative to the base path where all the recordings are stored.
     */
    public abstract String getJvmOptsForUseInBuild(String recordingsDirectoryRelativePath);

    public abstract List<String> getAdditionalGradleArgs(BuildExperimentSpec spec);

    public abstract void start(BuildExperimentSpec spec);

    public abstract void stop(BuildExperimentSpec spec);
}
