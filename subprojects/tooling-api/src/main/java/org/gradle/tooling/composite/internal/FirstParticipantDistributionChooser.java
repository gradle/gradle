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

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;

import java.util.Set;

class FirstParticipantDistributionChooser {
    static Distribution chooseDistribution(DistributionFactory distributionFactory, Set<GradleParticipantBuild> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants is empty. There must be at least one participant.");
        }
        // simply use the distribution of the first participant
        GradleParticipantBuild build = participants.iterator().next();
        if (build.getGradleDistribution() == null) {
            if (build.getGradleHome() == null) {
                if (build.getGradleVersion() == null) {
                    return distributionFactory.getDefaultDistribution(build.getProjectDir(), false);
                } else {
                    return distributionFactory.getDistribution(build.getGradleVersion());
                }
            } else {
                return distributionFactory.getDistribution(build.getGradleHome());
            }
        } else {
            return distributionFactory.getDistribution(build.getGradleDistribution());
        }
    }
}
