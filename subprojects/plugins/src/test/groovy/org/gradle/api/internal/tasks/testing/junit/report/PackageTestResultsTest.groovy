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
package org.gradle.api.internal.tasks.testing.junit.report

import spock.lang.Specification

import java.util.regex.Pattern

class PackageTestResultsTest extends Specification {

    public static final int MAX_FILENAME_LEN = 250

    def baseUrlIsSafeFileName(String testName){
        given:
        Pattern pattern = Pattern.compile("[a-zA-Z0-9\\.#_/\$-]+");

        when:
        def baseUrl = new PackageTestResults(testName, null).getBaseUrl()
        def matcher = pattern.matcher(baseUrl);

        then:
        baseUrl.length() < MAX_FILENAME_LEN
        matcher.matches()

        where:
        testName << [
            'abc ~#@*+%{}<>\\[]|"^' * 20,
            'ąęþó→↓←ß©ęœπąśðæŋ’ə…ł≤µń”„ćźż',
            '| customer1 | 127.0.0.1 | nod1 | 2 | MML command | /webapp/protocolSelection?$selected_rows.Customer=customer1&$selected_rows.ElementManagerIP=127.0.0.1&$selected_rows.Manager=10.0.0.1&$selected_rows.Node=nod1&$selected_rows.NodeAlias=nod1&$selected_rows.Region=region1&datasource=the_silo&FORCE_SHOW_APPLET=YES&login=user3&VNECLI_NODE_TYPE_CLASS_PARAMETERS=OSSRC_IP%3D127.0.0.1%7CNode%3Dnod1&Protocol=OSSRC_MML |'
        ]
    }
}
