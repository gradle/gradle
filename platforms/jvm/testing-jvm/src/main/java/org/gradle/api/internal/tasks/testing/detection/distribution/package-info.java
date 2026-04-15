/*
 * Copyright 2026 the original author or authors.
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

/**
 * Strategies for distributing discovered tests to worker processes.
 * <p>
 * When daemon-side test discovery is enabled, the daemon uses JUnit Platform's
 * {@code Launcher.discover()} API to build a {@code TestPlan} before forking workers.
 * The classes in this package determine how the discovered tests are grouped and sent
 * to {@link org.gradle.api.internal.tasks.testing.TestDefinitionProcessor}s for execution
 * in forked worker JVMs.
 *
 * @see org.gradle.api.internal.tasks.testing.detection.distribution.TestDistributor
 * @see org.gradle.api.internal.tasks.testing.detection.distribution.ByTopLevelContainerTestDistributor
 */
package org.gradle.api.internal.tasks.testing.detection.distribution;
