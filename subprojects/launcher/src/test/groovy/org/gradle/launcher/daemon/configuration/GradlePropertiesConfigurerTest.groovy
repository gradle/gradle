/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.daemon.configuration

import org.gradle.StartParameter
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/22/13
 */
class GradlePropertiesConfigurerTest extends Specification {

    private configurer = Spy(GradlePropertiesConfigurer)

    def "prepares properties and configures start parameter"() {
        //this test is kind of an overkill. Sorry...
        def param = Mock(StartParameter)
        def properties = Mock(GradleProperties)

        when:
        def out = configurer.configureStartParameter(param);

        then:
        param.getCurrentDir() >> new File("foo")
        param.isSearchUpwards() >> true
        param.getGradleUserHomeDir() >> new File("home")
        param.getMergedSystemProperties() >> ['x': '1']

        1 * configurer.prepareProperties(new File("foo"), true, new File("home"), ['x': '1']) >> properties
        1 * properties.updateStartParameter(_)
        out == properties
    }
}
