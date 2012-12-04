/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.samples

import org.junit.Rule
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.integtests.fixtures.Sample
import spock.lang.Specification

class SamplesAnnounceIntegrationTest extends Specification {
    @Rule GradleDistribution dist
    @Rule GradleDistributionExecuter executer
    @Rule Sample sample = new Sample("announce")

    def "make some announcements"() {
        // tweak sample to print all messages to standard out
        def initScript = sample.dir.createFile("init2.gradle")
        initScript << """
import org.gradle.api.plugins.announce.Announcer
import org.gradle.api.plugins.announce.internal.AnnouncerFactory

gradle.projectsEvaluated {
    rootProject.announce.announcerFactory = new AnnouncerFactory() {
        Announcer createAnnouncer(String type) {
            new Announcer() {
                void send(String title, String message) {
                    println message
                }
            }
        }
    }
}
        """

        when:
        def result = executer.inDirectory(sample.dir).withArguments("-I", initScript.path).withTasks("helloWorld").run()

        then:
        result.output.count("helloWorld completed!") == 2
    }
}
