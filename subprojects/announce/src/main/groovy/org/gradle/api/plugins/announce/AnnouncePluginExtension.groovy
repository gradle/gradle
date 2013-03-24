/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.internal.GradleDistributionLocator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.announce.internal.AnnouncerFactory
import org.gradle.api.plugins.announce.internal.DefaultAnnouncerFactory
import org.gradle.api.plugins.announce.internal.DefaultIconProvider

class AnnouncePluginExtension {
    private static final Logger logger = Logging.getLogger(AnnouncePlugin)

    /**
     * The username to use for announcements.
     */
    String username

    /**
     * The password to use for announcements.
     */
    String password

    private final onDemandLocalAnnouncer
    private final Project project
    AnnouncerFactory announcerFactory

    AnnouncePluginExtension(ProjectInternal project) {
        this.project = project
        this.announcerFactory = new DefaultAnnouncerFactory(this, project, new DefaultIconProvider(project.services.get(GradleDistributionLocator)))
        this.onDemandLocalAnnouncer = new LocalAnnouncer(this)
    }

    /**
     * Returns an {@link Announcer} that sends announcements to the local desktop, if a notification mechanism is available.
     *
     * @return The announcer.
     */
    Announcer getLocal() {
        return onDemandLocalAnnouncer
    }

    /**
     * Sets the {@link Announcer} that should be used to send announcements to the local desktop.
     */
    void setLocal(Announcer localAnnouncer) {
        onDemandLocalAnnouncer.local = localAnnouncer
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

    private static class LocalAnnouncer implements Announcer {
        AnnouncePluginExtension extension
        Announcer local

        LocalAnnouncer(AnnouncePluginExtension extension) {
            this.extension = extension
        }

        void send(String title, String message) {
            if (local == null) {
                local = extension.getAnnouncerFactory().createAnnouncer("local")
            }
            local.send(title, message)
        }
    }
}
