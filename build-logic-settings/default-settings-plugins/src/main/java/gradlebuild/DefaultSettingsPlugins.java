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

package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

public class DefaultSettingsPlugins implements Plugin<Settings> {

    private static final String RC_AND_MILESTONES_PATTERN =
        "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))";

    @Override
    public void apply(Settings settings) {
        settings.dependencyResolutionManagement(dependencyResolutionManagement ->
            dependencyResolutionManagement.getRepositories().maven(repository -> {
                repository.setUrl("https://repo.gradle.org/gradle/enterprise-libs-release-candidates");
                repository.content(content -> {
                    // DV plugin marker artifact
                    content.includeVersionByRegex("com.gradle.develocity", "com.gradle.develocity.gradle.plugin", RC_AND_MILESTONES_PATTERN);
                    // DV plugin jar
                    content.includeVersionByRegex("com.gradle", "develocity-gradle-plugin", RC_AND_MILESTONES_PATTERN);
                });
            }));

        settings.getPluginManager().apply("com.gradle.develocity");
        settings.getPluginManager().apply("io.github.gradle.develocity-conventions-plugin");
        settings.getPluginManager().apply("org.gradle.toolchains.foojay-resolver-convention");
    }
}
