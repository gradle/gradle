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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.plugins.announce.AnnouncePluginExtension
import org.gradle.api.plugins.announce.Announcer
import org.gradle.api.plugins.announce.internal.jdk6.AppleScriptBackedGrowlAnnouncer
import org.gradle.internal.os.OperatingSystem

class DefaultAnnouncerFactory implements AnnouncerFactory {
    private final AnnouncePluginExtension announcePluginConvention
    private final IconProvider iconProvider
    private final ProcessOperations processOperations

    DefaultAnnouncerFactory(AnnouncePluginExtension announcePluginConvention, ProcessOperations processOperations, IconProvider iconProvider) {
        this.announcePluginConvention = announcePluginConvention
        this.iconProvider = iconProvider
        this.processOperations = processOperations
    }

    Announcer createAnnouncer(String type) {
        def announcer = createActualAnnouncer(type)
        return announcer ? new IgnoreUnavailableAnnouncer(announcer) : new UnknownAnnouncer()
    }

    private Announcer createActualAnnouncer(String type) {
        switch (type) {
            case "local":
                if (OperatingSystem.current().windows) {
                    return createActualAnnouncer("snarl")
                } else if (OperatingSystem.current().macOsX) {
                    return createActualAnnouncer("growl")
                } else {
                    return createActualAnnouncer("notify-send")
                }
            case "twitter":
                String username = announcePluginConvention.username
                String password = announcePluginConvention.password
                return new Twitter(username, password)
            case "notify-send":
                return new NotifySend(processOperations, iconProvider)
            case "snarl":
                return new Snarl(iconProvider)
            case "growl":
                if (!java.awt.GraphicsEnvironment.isHeadless()) {
                    try {
                        return new AppleScriptBackedGrowlAnnouncer(iconProvider)
                    }
                    catch (AnnouncerUnavailableException e) {
                        // Ignore and fall back to growl notify
                    }
                }
                return new GrowlNotifyBackedAnnouncer(processOperations, iconProvider)
            default:
                return null
        }
    }
}

class UnknownAnnouncer implements Announcer {
    void send(String title, String message) {
        throw new InvalidUserDataException("unknown announcer type")
    }
}
