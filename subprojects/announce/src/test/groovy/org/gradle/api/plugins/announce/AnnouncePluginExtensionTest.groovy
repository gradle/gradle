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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.announce.internal.AnnouncerFactory
import org.gradle.util.HelperUtil
import spock.lang.Specification

class AnnouncePluginExtensionTest extends Specification {
    final AnnouncerFactory announcerFactory = Mock()
    final ProjectInternal project = HelperUtil.createRootProject()
    final AnnouncePluginExtension announcePluginConvention = new AnnouncePluginExtension(project)

    def setup() {
        announcePluginConvention.announcerFactory = announcerFactory
    }

    def announce() {
        Announcer announcer = Mock()

        when:
        announcePluginConvention.announce('someMessage', "someType")

        then:
        1 * announcerFactory.createAnnouncer("someType") >> announcer
        1 * announcer.send(project.name, 'someMessage')
    }

    def "creates and caches a local announcer on demand"() {
        Announcer announcer = Mock()

        when:
        announcePluginConvention.local.send("title", "message")
        announcePluginConvention.local.send("title2", "message2")

        then:
        1 * announcerFactory.createAnnouncer("local") >> announcer
        1 * announcer.send("title", "message")
        1 * announcer.send("title2", "message2")
    }

    def "can use a custom local announcer"() {
        Announcer announcer = Mock()

        given:
        def local = announcePluginConvention.local

        when:
        announcePluginConvention.local = announcer
        local.send("title", "message")

        then:
        1 * announcer.send("title", "message")
    }
}