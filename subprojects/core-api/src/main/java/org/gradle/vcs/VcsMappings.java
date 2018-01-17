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
 * Allows VCS mapping rules to be specified for a build. A VCS mapping rule is responsible for calculating the VCS information for a particular dependency.
 *
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
    /**
     * Adds a mapping rule that may define VCS information for any dependency. The supplied action is executed for each dependency.
     */
    VcsMappings addRule(String message, Action<? super VcsMapping> rule);

    /**
     * Adds a mapping rule that may define VCS information for the given module.
     */
    VcsMappings withModule(String groupName, Action<? super VcsMapping> rule);

    /**
     * Creates and configures a {@link VersionControlSpec} instance to pass to {@link VcsMapping#from(VersionControlSpec)}.
     */
    <T extends VersionControlSpec> T vcs(Class<T> type, Action<? super T> configuration);
}
