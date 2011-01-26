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

package org.gradle.api.plugins.announce

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.announce.internal.AnnouncerFactory
import org.gradle.api.plugins.announce.internal.DefaultAnnouncerFactory
import org.gradle.api.logging.Logging
import org.gradle.api.logging.Logger

/**
 * This plugin allows to send announce messages to Twitter.
 *
 * @author hackergarten
 */
class AnnouncePlugin implements Plugin<Project> {
    void apply(Project project) {
        project.convention.plugins.announce = new AnnouncePluginConvention(project)
    }
}

class AnnouncePluginConvention {
    private static final Logger logger = Logging.getLogger(AnnouncePlugin)

    /**
     * The username to use for announcements.
     */
    String username

    /**
     * The password to use for announcements.
     */
    String password

    Project project
    AnnouncerFactory announcerFactory

    AnnouncePluginConvention(project) {
        this.project = project
        this.announcerFactory = new DefaultAnnouncerFactory(this)
    }

    /**
     * Configures the announce plugin convention. The given closure configures the {@link AnnouncePluginConvention}.
     */
    void announce(Closure closure) {
        closure.delegate = this
        closure()
    }

    /**
     * Sends an announcement of the given type.
     *
     * @param msg The content of the announcement
     * @param type The announcement type.
     */
    void announce(String msg, String type) {
        try {
            announcerFactory.createAnnouncer(type).send(project.name, msg)
        } catch (Exception e) {
            logger.warn("Failed to send message '$msg' to '$type'", e)
        }
    }
}

