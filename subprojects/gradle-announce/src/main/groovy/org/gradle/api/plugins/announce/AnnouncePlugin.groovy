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


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.announce.internal.AnnouncerFactory
import org.gradle.api.plugins.announce.internal.DefaultAnnouncerFactory

/**
 * This plugin allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class AnnouncePlugin implements Plugin<Project> {
    public void apply(final Project project) {
        project.convention.plugins.announce = new AnnouncePluginConvention(project)
    }
}

class AnnouncePluginConvention {
    /**
     * The username to use for announcements
     */
    String username

    /**
     * The password to use for announcements
     */
    String password

    Project project
    AnnouncerFactory announcerFactory

    def AnnouncePluginConvention(project) {
        this.project = project;
        this.announcerFactory = new DefaultAnnouncerFactory(this)
    }

    /**
     * Configures the announce plugin convention. The given closure configures the {@link AnnouncePluginConvention}.
     */
    def announce(Closure closure) {
        closure.delegate = this
        closure()
    }

    /**
     * Sends an announcement of the given type.
     * @param msg The content of the announcement
     * @param type The announcement type.
     */
    def announce(String msg, def type) {
        announcerFactory.createAnnouncer(type).send(project.name, msg)
    }
}

