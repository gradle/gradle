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

package org.gradle.api.plugins.osgi

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.jar.JarFile
import java.util.jar.Manifest

@TestReproducibleArchives
class OsgiPluginIntegrationSpec extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-2237")
    def "can set modelled manifest properties with instruction"() {
        given:
        buildFile << """
            version = "1.0"
            group = "foo"
            apply plugin: "java"
            apply plugin: "osgi"

            jar {
                manifest {
                    version = "3.0"
                    instructionReplace("Bundle-Version", "2.0")
                    instructionReplace("Bundle-SymbolicName", "bar")
                }
            }

            assert jar.manifest.symbolicName.startsWith("bar") // GRADLE-2446
        """

        and:
        file("src/main/java/Thing.java") << "public class Thing {}"

        when:
        run "jar"

        def manifestText = file("build/tmp/jar/MANIFEST.MF").text
        then:
        manifestText.contains("Bundle-Version: 2.0")
        manifestText.contains("Bundle-SymbolicName: bar")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2237")
    @IgnoreIf({GradleContextualExecuter.parallel})
    def "jar task remains incremental"() {
        given:
        // Unsure why, but this problem doesn't show if we don't wait a little bit
        // before the next execution.
        //
        // The value that's used is comes from aQute.lib.osgi.Analyzer#calcManifest()
        // and is set to the current time. I don't have an explanation for why the sleep is needed.
        // It needs to be about 1000 on my machine.
        def sleepTime = 1000

        buildFile << """
            apply plugin: "java"
            apply plugin: "osgi"

            jar {
                manifest {
                    instruction "Bnd-LastModified", "123"
                }
            }
        """

        and:
        file("src/main/java/Thing.java") << "public class Thing {}"

        when:
        run "jar"

        then:
        ":jar" in nonSkippedTasks

        when:
        sleep sleepTime
        run "jar"

        then:
        ":jar" in skippedTasks

        when:
        sleep sleepTime
        run "clean", "jar"

        then:
        ":jar" in nonSkippedTasks
    }

    @Issue('GRADLE-3374')
    def "can merge manifests containing split multi-byte chars"() {
        given:
        def attributeNameMerged = 'Looong-Name-Of-Manifest-Entry'
        def attributeNameWritten = 'Another-Looooooong-Name-Entry'
        // Means 'long russian text'
        def attributeValue = 'com.acme.example.pack.**, длинный.текст.на.русском.языке.**'

        def mergedManifestFilename = 'manifest-with-split-multi-byte-char.txt'
        def mergedManifest = new Manifest()
        mergedManifest.mainAttributes.putValue('Manifest-Version', '1.0')
        mergedManifest.mainAttributes.putValue(attributeNameMerged, attributeValue)
        def mergedManifestFile = file(mergedManifestFilename)
        mergedManifestFile.withOutputStream { mergedManifest.write(it) }

        buildScript """
            apply plugin: 'java'
            apply plugin: 'osgi'

            jar {
                destinationDir = file('dest')
                archiveName = 'test.jar'
                manifest {
                    version = '3.0'
                    instruction 'Bnd-$attributeNameWritten', '$attributeValue'
                    attributes '$attributeNameWritten': '$attributeValue'
                    from file('$mergedManifestFilename')
                }
            }
        """.stripIndent()
        file("src/main/java/Thing.java") << "public class Thing {}"

        when:
        executer.withDefaultCharacterEncoding('windows-1252').withTasks('jar')
        executer.run()

        then:
        def jar = new JarFile(file('dest/test.jar'))
        try {
            def manifest = jar.manifest
            assert manifest.mainAttributes.getValue("Bnd-$attributeNameWritten") == attributeValue
            assert manifest.mainAttributes.getValue(attributeNameWritten) == attributeValue
            assert manifest.mainAttributes.getValue(attributeNameMerged) == attributeValue
        } finally {
            jar.close();
        }
    }

    @Issue("GRADLE-3487")
    def "generates import package attributes when the package is available in multiple bundles"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'osgi'

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
        succeeds 'jar'

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
