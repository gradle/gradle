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

package org.gradle.internal.scan.scopeids;

import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;

/**
 * Obtained from the Gradle object services.
 * Exists to remove linkage against types such as {@link BuildInvocationScopeId} and friends.
 */
@UsedByScanPlugin
public interface BuildScanScopeIds {

    String getBuildInvocationId();

    String getWorkspaceId();

    String getUserId();

}
