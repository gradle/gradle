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
package org.gradle.tooling.internal.consumer;

import org.gradle.internal.composite.GradleParticipantBuild;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultCompositeConnectionParameters extends AbstractConnectionParameters implements CompositeConnectionParameters {
    private final List<GradleParticipantBuild> builds;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ProjectConnectionParameters connectionParameters) {
        return (Builder) new Builder().setDaemonMaxIdleTimeUnits(connectionParameters.getDaemonMaxIdleTimeUnits()).
            setDaemonMaxIdleTimeValue(connectionParameters.getDaemonMaxIdleTimeValue()).
            setEmbedded(connectionParameters.isEmbedded()).
            setGradleUserHomeDir(connectionParameters.getGradleUserHomeDir()).
            setVerboseLogging(connectionParameters.getVerboseLogging());
    }

    @Override
    public List<GradleParticipantBuild> getBuilds() {
        return builds;
    }

    public static class Builder extends AbstractConnectionParameters.Builder {
        private final List<GradleParticipantBuild> builds = new ArrayList<GradleParticipantBuild>();

        private Builder() {
            super();
        }

        public DefaultCompositeConnectionParameters build() {
            return new DefaultCompositeConnectionParameters(gradleUserHomeDir, embedded,
                daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging, builds);
        }

        public void setBuilds(Collection<GradleParticipantBuild> builds) {
            this.builds.clear();
            this.builds.addAll(builds);
        }
    }

    private DefaultCompositeConnectionParameters(File gradleUserHomeDir, Boolean embedded,
                                                 Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                                 boolean verboseLogging, List<GradleParticipantBuild> builds) {
        super(gradleUserHomeDir, embedded, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging);
        this.builds = Collections.unmodifiableList(builds);
    }
}
