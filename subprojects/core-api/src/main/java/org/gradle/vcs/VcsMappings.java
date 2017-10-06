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

package org.gradle.vcs;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * In settings.gradle:
 * <pre>
 * vcsMappings {
 *   withModule("group:name") {
 *     from vcs(GitVcs) {
 *         url = "..."
 *     }
 *   }
 *   addRule("rule for group") { details -&gt;
 *       if (details.requested.group == "group") {
 *           from vcs(GitVcs) {
 *               url = "..."
 *           }
 *       }
 *   }
 * }
 * </pre>
 * @since 4.4
 */
@Incubating
@HasInternalProtocol
public interface VcsMappings {
    VcsMappings addRule(String message, Action<VcsMapping> rule);
    VcsMappings withModule(String groupName, Action<VcsMapping> rule);
    <T extends VersionControlSpec> T vcs(Class<T> type, Action<? super T> configuration);
}
