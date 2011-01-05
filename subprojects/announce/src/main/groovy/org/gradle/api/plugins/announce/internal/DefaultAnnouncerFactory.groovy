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
package org.gradle.api.plugins.announce.internal

import org.gradle.api.plugins.announce.AnnouncePluginConvention
import org.gradle.api.plugins.announce.Announcer

/**
 * @author Hans Dockter
 */
class DefaultAnnouncerFactory implements AnnouncerFactory {
    AnnouncePluginConvention announcePluginConvention

    def DefaultAnnouncerFactory(announcePluginConvention) {
        this.announcePluginConvention = announcePluginConvention;
    }

    Announcer createAnnouncer(String type) {
        if (type == "twitter") {
            String username = announcePluginConvention.username
            String password = announcePluginConvention.password
            return new Twitter(username, password)
        } else if (type == "notify-send") {
            return new NotifySend()
        } else if (type == "snarl") {
            return new Snarl()
        } else if (type == "growl") {
            return new Growl()
        }
        new DoNothingAnnouncer()
    }
}

class DoNothingAnnouncer implements Announcer {
    void send(String title, String message) {
        // do nothing
    }
}
