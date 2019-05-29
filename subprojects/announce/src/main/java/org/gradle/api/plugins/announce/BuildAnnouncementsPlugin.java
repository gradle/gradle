/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.announce;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.announce.internal.AnnouncingBuildListener;
import org.gradle.util.SingleMessageLogger;

/**
 * A plugin which announces interesting build lifecycle events.
 *
 * @deprecated This plugin will be removed in the next major version.
 */
@Deprecated
public class BuildAnnouncementsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(AnnouncePlugin.class);
        SingleMessageLogger.nagUserOfDeprecatedPlugin("Build Announcements");
        AnnouncePluginExtension extension = project.getExtensions().findByType(AnnouncePluginExtension.class);
        AnnouncingBuildListener listener = new AnnouncingBuildListener(extension.getLocal());
        project.getGradle().addBuildListener(listener);
    }
}
