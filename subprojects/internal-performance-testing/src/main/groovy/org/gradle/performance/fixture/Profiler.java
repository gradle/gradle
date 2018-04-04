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
        String targetDir = System.getProperty(TARGET_DIR_KEY);
        if (targetDir != null && !Jvm.current().isIbmJvm()) {
            return new JfrProfiler(new File(targetDir));
        } else {
            return new NoopProfiler();
        }
    }

    public abstract List<String> getAdditionalJvmOpts(BuildExperimentSpec spec);

    public abstract List<String> getAdditionalGradleArgs(BuildExperimentSpec spec);

    public abstract void start(BuildExperimentSpec spec);

    public abstract void stop(BuildExperimentSpec spec);
}
