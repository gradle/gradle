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
package org.gradle.plugins.signing

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*
import org.gradle.util.*
import static org.gradle.util.TextUtil.*
import org.junit.*

abstract class SigningIntegrationSpec extends AbstractIntegrationSpec {
    
    @Rule public final TestResources resources = new TestResources("keys")

    def setup() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'signing'
            archivesBaseName = 'sign'
        """
    }

    static class KeyInfo {
        String keyId
        String password
        String keyRingFilePath
        
        Map<String, String> asProperties(String name = null) {
            def prefix = name ? "signing.${name}." : "signing."
            def properties = [:]
            properties[prefix + "keyId"] = keyId
            properties[prefix + "password"] = password
            properties[prefix + "secretKeyRingFile"] = keyRingFilePath
            properties
        }
        
        String addAsPropertiesScript(addTo = "project", name = null) {
            asProperties(name).collect { k, v ->
                "${addTo}.setProperty('${escapeString(k)}', '${escapeString(v)}')"
            }.join(";")
        }
    }
    
    KeyInfo getKeyInfo(set = "default") {
        new KeyInfo(
            keyId: file(set, "keyId.txt").text.trim(),
            password: file(set, "password.txt").text.trim(),
            keyRingFilePath: file(set, "secring.gpg")
        )
    }
    
    String getJavadocAndSourceJarsScript(String configurationName = null) {
        def tasks = """
            task("sourcesJar", type: Jar, dependsOn: classes) { 
                classifier = 'sources' 
                from sourceSets.main.allSource
            } 

            task("javadocJar", type: Jar, dependsOn: javadoc) { 
                classifier = 'javadoc' 
                from javadoc.destinationDir 
            } 
        """
        
        if (configurationName == null) {
            tasks
        } else {
            tasks + """
                configurations {
                    $configurationName
                }
                
                artifacts {
                    $configurationName sourcesJar, javadocJar
                }
            """
        }
    }
    
}