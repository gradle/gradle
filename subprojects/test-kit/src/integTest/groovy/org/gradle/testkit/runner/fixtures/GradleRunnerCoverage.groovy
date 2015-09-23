/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.fixtures

import static org.gradle.testkit.runner.fixtures.GradleRunnerType.DAEMON
import static org.gradle.testkit.runner.fixtures.GradleRunnerType.EMBEDDED

class GradleRunnerCoverage {
    public static final EnumSet<GradleRunnerType> ALL = EnumSet.of(DAEMON, EMBEDDED)
    public static final EnumSet<GradleRunnerType> FORKED = EnumSet.of(DAEMON)
    public static final EnumSet<GradleRunnerType> DEBUG = EnumSet.of(EMBEDDED)
}
