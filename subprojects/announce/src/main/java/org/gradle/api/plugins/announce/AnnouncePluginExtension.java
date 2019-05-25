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

import org.gradle.api.Project;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.announce.internal.AnnouncerFactory;
import org.gradle.api.plugins.announce.internal.DefaultAnnouncerFactory;
import org.gradle.api.plugins.announce.internal.DefaultIconProvider;
import org.gradle.internal.installation.CurrentGradleInstallation;

/**
 * <p>The extension used by the AnnouncePlugin.</p>
 */
@Deprecated
public class AnnouncePluginExtension {
    private final Logger logger = Logging.getLogger(getClass());
    private final LocalAnnouncer onDemandLocalAnnouncer;
    private final Project project;

    private String username;
    private String password;
    private AnnouncerFactory announcerFactory;

    public AnnouncePluginExtension(ProjectInternal project) {
        this.project = project;
        this.announcerFactory = new DefaultAnnouncerFactory(this, project.getServices().get(ProcessOperations.class), new DefaultIconProvider(project.getServices().get(CurrentGradleInstallation.class).getInstallation()));
        this.onDemandLocalAnnouncer = new LocalAnnouncer(this);
    }

    /**
     * Returns an {@link Announcer} that sends announcements to the local desktop, if a notification mechanism is available.
     *
     * @return The announcer.
     */
    public Announcer getLocal() {
        return onDemandLocalAnnouncer;
    }

    /**
     * Sets the {@link Announcer} that should be used to send announcements to the local desktop.
     */
    public void setLocal(Announcer localAnnouncer) {
        onDemandLocalAnnouncer.setLocal(localAnnouncer);
    }

    /**
     * Sends an announcement of the given type.
     *
     * @param msg The content of the announcement
     * @param type The announcement type.
     */
    public void announce(String msg, String type) {
        try {
            announcerFactory.createAnnouncer(type).send(project.getName(), msg);
        } catch (Exception e) {
            logger.warn("Failed to send message \'" + msg + "\' to \'" + type + "\'", e);
        }
    }

    /**
     * The username to use for announcements.
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The password to use for announcements.
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AnnouncerFactory getAnnouncerFactory() {
        return announcerFactory;
    }

    public void setAnnouncerFactory(AnnouncerFactory announcerFactory) {
        this.announcerFactory = announcerFactory;
    }

    private static class LocalAnnouncer implements Announcer {
        private AnnouncePluginExtension extension;
        private Announcer local;

        public LocalAnnouncer(AnnouncePluginExtension extension) {
            this.extension = extension;
        }

        @Override
        public void send(String title, String message) {
            if (local == null) {
                local = extension.getAnnouncerFactory().createAnnouncer("local");
            }
            local.send(title, message);
        }

        public AnnouncePluginExtension getExtension() {
            return extension;
        }

        public void setExtension(AnnouncePluginExtension extension) {
            this.extension = extension;
        }

        public Announcer getLocal() {
            return local;
        }

        public void setLocal(Announcer local) {
            this.local = local;
        }
    }
}
