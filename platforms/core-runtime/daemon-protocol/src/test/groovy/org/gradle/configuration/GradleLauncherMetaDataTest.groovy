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
package org.gradle.configuration

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.SetSystemProperties

class GradleLauncherMetaDataTest extends Specification {
    @Rule public final SetSystemProperties sysProps = new SetSystemProperties()

    def usesSystemPropertyToDetermineApplicationName() {
        System.setProperty("org.gradle.appname", "some-gradle-launcher")
        StringWriter writer = new StringWriter()
        GradleLauncherMetaData metaData = new GradleLauncherMetaData()

        when:
        metaData.describeCommand(writer, "[options]", "<task-name>")

        then:
        writer.toString() == "some-gradle-launcher [options] <task-name>"
    }

    def usesDefaultApplicationNameWhenSystemPropertyNotSet() {
        System.clearProperty("org.gradle.appname")
        StringWriter writer = new StringWriter()
        GradleLauncherMetaData metaData = new GradleLauncherMetaData()

        when:
        metaData.describeCommand(writer, "[options]", "<task-name>")

        then:
        writer.toString() == "gradle [options] <task-name>"
    }
}
