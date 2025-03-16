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

package org.gradle.internal.scan.time;

import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Used to determine when the build was started.
 *
 * This is effectively a build scan specific view of {@link BuildStartedTime}.
 * @since 4.2
 */
@ServiceScope(Scope.BuildTree.class)
public interface BuildScanBuildStartedTime {

    long getBuildStartedTime();

}
