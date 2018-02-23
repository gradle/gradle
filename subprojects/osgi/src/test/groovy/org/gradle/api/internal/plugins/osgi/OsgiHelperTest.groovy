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
package org.gradle.api.internal.plugins.osgi;

import spock.lang.Specification

public class OsgiHelperTest extends Specification {
    def "convert to OSGi-compliant version"() {
        def helper = new OsgiHelper()

        expect:
        helper.getVersion(projectVersion) == osgiVersion

        where:
        projectVersion   | osgiVersion
        "1"              | "1"
        "1.2"            | "1.2"
        "1.2.3"          | "1.2.3"
        "1.2.3.4"        | "1.2.3.4"
        "1.2.3.4.5"      | "1.2.3.4_5"
        "1.2.3.4.5.6"    | "1.2.3.4_5_6"
        "1.2.3.4.5.6.7"  | "1.2.3.4_5_6_7"
        "1.2.3.4.5-6.7"  | "1.2.3.4_5-6_7"
        "1.2.3.4-5.6.7"  | "1.2.3.4-5_6_7"
        "1.2.3.ABC"      | "1.2.3.ABC"
        "1.2.ABC"        | "1.2.0.ABC"
        "1.ABC"          | "1.0.0.ABC"
        "1-ABC"          | "1.0.0.ABC"
        "1-20110303"     | "1.0.0.20110303"
        "1.2-20110303"   | "1.2.0.20110303"
        "1.2.3-20110303" | "1.2.3.20110303"
        "1_20110303"     | "1.0.0.20110303"
        "1.2_20110303"   | "1.2.0.20110303"
        "1.2.3_20110303" | "1.2.3.20110303"
        "1.2.3_20110303" | "1.2.3.20110303"
        "1*20110303"     | "1.0.0.20110303"
        "1*20110303"     | "1.0.0.20110303"
        "1.2*20110303"   | "1.2.0.20110303"
        "1.2.3*20110303" | "1.2.3.20110303"
        "1@20110303"     | "1.0.0.20110303"
        "1.2@20110303"   | "1.2.0.20110303"
        "1.2.3@20110303" | "1.2.3.20110303"
        "1!20110303"     | "1.0.0.20110303"
        "1.2!20110303"   | "1.2.0.20110303"
        "1.2.3!20110303" | "1.2.3.20110303"
        "1%20110303"     | "1.0.0.20110303"
        "1.2%20110303"   | "1.2.0.20110303"
        "1.2.3%20110303" | "1.2.3.20110303"
        "1^20110303"     | "1.0.0.20110303"
        "1.2^20110303"   | "1.2.0.20110303"
        "1.2.3^20110303" | "1.2.3.20110303"
        "1/20110303"     | "1.0.0.20110303"
        "1.2/20110303"   | "1.2.0.20110303"
        "1.2.3/20110303" | "1.2.3.20110303"
        '1-20.11$03@0#3' | "1.0.0.20_11_03_0_3"
        "100000"         | "100000"
    }
}
