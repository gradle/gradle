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
package org.gradle.api.plugins.announce.internal

import org.gradle.api.internal.ProcessOperations
import org.gradle.internal.os.OperatingSystem

class GrowlNotifyBackedAnnouncer extends Growl {
    private final ProcessOperations processOperations
    private final IconProvider iconProvider

    GrowlNotifyBackedAnnouncer(ProcessOperations processOperations, IconProvider iconProvider) {
        this.processOperations = processOperations
        this.iconProvider = iconProvider
    }

    void send(String title, String message) {
        def exe = OperatingSystem.current().findInPath('growlnotify')
        if (exe == null) {
            throw new AnnouncerUnavailableException("Could not find 'growlnotify' in path.")
        }
        processOperations.exec {
            executable exe
            args '-m', message
            def icon = iconProvider.getIcon(48, 48)
            if (icon) {
                args '--image', icon.absolutePath
            }
            args '-t', title
        }
    }

    private def escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r")
    }
}
