/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

class BuildScanPluginSmokeTest extends AbstractSmokeTest {
    @Requires(TestPrecondition.ONLINE)
    @Unroll
    def "can run build with build scan plugin #version"() {
        def initScript = file("init.gradle") << """
initscript {
    repositories {
        maven { url "https://plugins.gradle.org/m2" }
    }
    dependencies {
        classpath "com.gradle:build-scan-plugin:${version}"
    }
}
rootProject {
    apply plugin: com.gradle.scan.plugin.BuildScanPlugin
    buildScan {
          licenseAgreementUrl = 'https://gradle.com/terms-of-service'
          licenseAgree = 'yes'
    }
}
        """

        buildFile << """
            apply plugin: 'java'
            repositories { jcenter() }

            dependencies { 
                testCompile 'junit:junit:4.12' 
            }
        """

        file("src/main/java/MySource.java") << """
            public class MySource {
                public static boolean isTrue() { return true; }
            }
        """

        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertTrue(MySource.isTrue());
               }
            }
        """

        when:
        def result = runner("build", "-I", initScript.toString(), "-Dscan").forwardOutput().build()
        then:
        result.output.contains("Publishing build information")
        result.output.contains("https://gradle.com/s/")

        where:
        version << ["1.7.1"]
    }
}
