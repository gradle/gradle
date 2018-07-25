/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scan;

import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.scan.config.BuildScanConfigServices;
import org.gradle.internal.scan.time.BuildScanBuildStartedTime;
import org.gradle.internal.scan.time.BuildScanClock;
import org.gradle.internal.scan.time.DefaultBuildScanBuildStartedTime;
import org.gradle.internal.scan.time.DefaultBuildScanClock;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.time.Clock;

public class BuildScanServices {

    BuildScanClock createBuildScanClock(Clock clock) {
        return new DefaultBuildScanClock(clock);
    }

    BuildScanBuildStartedTime createBuildScanBuildStartedTime(BuildStartedTime buildStartedTime) {
        return new DefaultBuildScanBuildStartedTime(buildStartedTime);
    }

    void configure(ServiceRegistration registration) {
        registration.addProvider(new BuildScanConfigServices());
    }

}
