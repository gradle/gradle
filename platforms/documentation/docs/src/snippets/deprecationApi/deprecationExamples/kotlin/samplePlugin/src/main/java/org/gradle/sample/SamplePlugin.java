/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.deprecation.source.ReportSource;

import javax.inject.Inject;

public abstract class SamplePlugin implements Plugin<Project> {

    @Inject
    public abstract Problems getProblems();

    @Override
    public void apply(Project project) {
        getProblems().getDeprecationReporter().deprecatePlugin(
            ReportSource.plugin("org.gradle.sample.plugin"),
            "org.gradle.sample.plugin",
            spec -> spec
                .removedInVersion("2.0.0")
                .replacedBy("org.gradle.sample.newer-plugin")
                .details("""
                        We decided to rename the plugin to better reflect its purpose.
                        You can find the new plugin at https://plugins.gradle.org/org.gradle.sample.newer-plugin
                    """)
        );

        project.getExtensions().create("sampleExtension", SampleExtension.class);
    }

}
