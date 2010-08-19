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

import org.gradle.api.Project
import org.gradle.util.HelperUtil
import spock.lang.Specification
import org.gradle.api.plugins.announce.internal.AnnouncerFactory

/**
 * @author Hans Dockter
 */

class AnnouncePluginConventionTest extends Specification {
    Project project = HelperUtil.createRootProject()
    AnnouncePluginConvention announcePluginConvention = new AnnouncePluginConvention(project)

    def announceConfigureMethod() {
        when:
        announcePluginConvention.announce {
            username = 'someUser'
            password = 'somePassword'
        }

        then:
        announcePluginConvention.username == 'someUser'
        announcePluginConvention.password == 'somePassword'
    }

    def announce() {
        AnnouncerFactory announcerFactory = Mock()
        announcePluginConvention.announcerFactory = announcerFactory
        Announcer announcer = Mock()
        announcerFactory.createAnnouncer("someType") >> announcer

        when:
        announcePluginConvention.announce('someMessage', "someType")

        then:
        1 * announcer.send(project.name, 'someMessage')
    }
}