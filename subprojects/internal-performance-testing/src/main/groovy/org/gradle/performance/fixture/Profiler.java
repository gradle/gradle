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

public abstract class Profiler implements DataCollector {
    private static final String TARGET_DIR_KEY = "org.gradle.performance.flameGraphTargetDir";

    public static Profiler create() {
        String targetDir = System.getProperty(TARGET_DIR_KEY);
        if (targetDir != null && !Jvm.current().isIbmJvm()) {
            return new JfrProfiler(new File(targetDir));
        } else {
            return new NoopProfiler();
        }
    }

    public abstract void setVersionUnderTest(String versionUnderTest);

    public abstract void setScenarioUnderTest(String scenarioUnderTest);

    public abstract void setUseDaemon(boolean useDaemon);
}
