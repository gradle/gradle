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

package org.gradle.api.plugins.announce.internal;


import org.gradle.api.internal.ProcessOperations
import org.gradle.api.plugins.announce.Announcer
import org.gradle.internal.os.OperatingSystem

/**
 * This class wraps the Ubuntu Notify Send functionality.
 */
class NotifySend implements Announcer {
    private final IconProvider iconProvider
    private final ProcessOperations processOperations

    NotifySend(ProcessOperations processOperations, IconProvider iconProvider) {
        this.processOperations = processOperations
        this.iconProvider = iconProvider
    }

    void send(String title, String message) {
        File exe = OperatingSystem.current().findInPath("notify-send")
        if (exe == null) {
            throw new AnnouncerUnavailableException("Could not find 'notify-send' in the path.")
        }
        processOperations.exec {
            executable exe
            def icon = iconProvider.getIcon(32, 32)
            if (icon) {
                args '-i', icon.absolutePath
            }
            args '--hint=int:transient:1'
            args title, message
        }
    }
}
