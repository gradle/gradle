/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.osgi

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import java.util.jar.JarFile

class BndBundleIntegrationTest extends AbstractIntegrationSpec {

    def "can use BND Bundle task and produces proper bundle information"() {
        given:
        buildFile << """
            apply plugin: 'java'

            buildscript {
                repositories {
                    mavenCentral()
                }

                dependencies {
                    classpath 'biz.aQute.bnd:biz.aQute.bnd.gradle:3.2.0'
                }
            }

            task bundle(type: aQute.bnd.gradle.Bundle) {
                from sourceSets.main.output
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.eclipse:osgi:3.10.0-v20140606-1445'
                compile 'org.eclipse.equinox:common:3.6.200-v20130402-1505'
            }
        """

        settingsFile << """
            rootProject.name = 'test'
        """

        file('src/main/java/org/gradle/Bar.java') << """
            package org.gradle;

            import org.eclipse.core.runtime.URIUtil;
            import java.net.*;

            public class Bar {
                public Bar() throws URISyntaxException {
                    URI uri = URIUtil.fromString("file:/test");
                }
            }
        """

        when:
        succeeds 'bundle'

        then:
        def jar = new JarFile(file('build/libs/test.jar'))

        try {
            def manifest = jar.manifest
            assert manifest.mainAttributes.getValue('Import-Package') == 'org.eclipse.core.runtime;version="[3.4,4)";common=split'
        } finally {
            jar.close();
        }
    }
}
