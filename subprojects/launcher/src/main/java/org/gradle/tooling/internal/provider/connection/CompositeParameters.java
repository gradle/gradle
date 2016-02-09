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

package org.gradle.tooling.internal.provider.connection;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CompositeParameters implements Serializable {
    private final List<GradleParticipantBuild> builds;
    private final File daemonBaseDir;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;

    public CompositeParameters(List<GradleParticipantBuild> builds, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        this.builds = builds;
        this.daemonBaseDir = daemonBaseDir != null ? new File(daemonBaseDir.getAbsolutePath()) : null;
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
    }

    public List<GradleParticipantBuild> getBuilds() {
        return builds;
    }

    public File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return daemonMaxIdleTimeValue;
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return daemonMaxIdleTimeUnits;
    }
}
