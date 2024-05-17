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

/**
 * Allows VCS mapping rules to be specified for a build. A VCS mapping rule is responsible for calculating the VCS information for a particular dependency.
 *
 * In settings.gradle:
 * <pre class='autoTestedSettingsWithoutCC'>
 * sourceControl {
 *   vcsMappings {
 *     withModule("org.gradle:gradle") {
 *       from(GitVersionControlSpec) {
 *         url = uri("https://github.com/gradle/gradle")
 *       }
 *     }
 *     all { details -&gt;
 *       if (details.requested.group == "org.gradle") {
 *         from(GitVersionControlSpec) {
 *           url = uri("https://github.com/gradle/${details.requested.module}")
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @since 4.4
 */
public interface VcsMappings {
    /**
     * Adds a mapping rule that may define VCS information for any dependency. The supplied action is executed for all components.
     *
     * @since 4.6
     */
    VcsMappings all(Action<? super VcsMapping> rule);

    /**
     * Adds a mapping rule that may define VCS information for the given module. The supplied action is executed when the given module is required.
     *
     * @param module The module to apply the rule to, in the form "group:module".
     */
    VcsMappings withModule(String module, Action<? super VcsMapping> rule);
}
