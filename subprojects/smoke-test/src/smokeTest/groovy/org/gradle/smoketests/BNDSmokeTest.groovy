/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Issue

/**
 * Smoke tests for <a href="https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md">the BND plugin</a>.
 */
class BNDSmokeTest extends AbstractPluginValidatingSmokeTest {
    def setup() {
        settingsFile << """
pluginManagement {
    plugins {
        id "biz.aQute.bnd.builder" version "${TestedVersions.bnd}"
    }
}

rootProject.name = 'bnd-smoke-test'
"""
    }

    def "BND plugin writes direct external dependency versions to the manifest"() {
        given:
        def commonsVersion = "3.12.0"
        def calculatedCommonsVersionRange = "[3.12,4)"
        buildFile << """
${addBNDBuilderPlugin()}

dependencies {
    implementation "org.apache.commons:commons-lang3:$commonsVersion"
}

tasks.named("jar") {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support

        bnd('Import-Package': 'org.apache.*')
    }
}
"""

        file("src/main/java/com/example/Example.java") << """
package com.example;

import org.apache.commons.lang3.StringUtils;

public class Example {
    public boolean testIsEmpty(String someString) {
        return StringUtils.isEmpty(someString);
    }
}
"""

        when:
        runner("jar")
            .forwardOutput()
            .deprecations {
                expectAbstractTaskConventionDeprecationWarning {
                    cause = "plugin 'biz.aQute.bnd.builder'"
                }
            }
            .build()

        then: "version numbers exist in the manifest"
        assertJarManifestContains("Import-Package", "org.apache.commons.lang3;version=\"$calculatedCommonsVersionRange\"")
    }

    def "BND plugin writes transitive external dependency versions of a direct project dependency to the manifest"() {
        given:
        def commonsVersion = "3.12.0"
        def directVersion = "2.7-SNAPSHOT"
        def calculatedDirectVersionRange = "[2.7,3)"
        settingsFile << """
include "direct"
"""
        buildFile << """
${addBNDBuilderPlugin()}

dependencies {
    implementation project(":direct")
}

tasks.named("jar") {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support

        bnd('Import-Package': 'com.example.util.*')
    }
}
"""

        file("src/main/java/com/example/Example.java") << """
package com.example;

import com.example.util.MyUtil;

public class Example {
    public boolean testIsEmpty(String someString) {
        return MyUtil.myIsEmpty(someString);
    }
}
"""

        and:
        file("direct/build.gradle") << """
plugins {
    id "java-library"
    id "biz.aQute.bnd.builder"
}

group = "com.example.direct"
version = "$directVersion"

${mavenCentralRepository()}

dependencies {
    api "org.apache.commons:commons-lang3:$commonsVersion"
}

jar {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support

        bnd("-exportcontents": "com.example.util.*")
    }
}
"""

        file("direct/src/main/java/com/example/util/MyUtil.java") << """
package com.example.util;

import org.apache.commons.lang3.StringUtils;

public class MyUtil {
    public static boolean myIsEmpty(String someString) {
        return StringUtils.isEmpty(someString);
    }
}
"""

        when:
        runner(":jar")
            .forwardOutput()
            .deprecations {
                expectAbstractTaskConventionDeprecationWarning {
                    cause = "plugin 'biz.aQute.bnd.builder'"
                }
            }
            .build()

        then: "version numbers exist in the manifest"
        assertJarManifestContains("Import-Package", "com.example.util;version=\"$calculatedDirectVersionRange\"")
    }

    @Issue("https://github.com/bndtools/bnd/pull/5701")
    def "BND bundle added manually to a Jar task writes external dependency versions to the manifest"() {
        given:
        def directVersion = "1.5.8"
        def calculatedDirectVersionRange = "[1.5,2)"
        settingsFile << """
include "direct"
"""
        buildFile << """
import aQute.bnd.gradle.BundleTaskExtension

plugins {
    id 'biz.aQute.bnd.builder' apply false
    id 'java-library'
}

dependencies {
    implementation project(":direct")
}

// Replicate the JUnit 5 build, which lazily configures the Jar task to add the bundle extension and its action but does NOT apply any BND plugins
tasks.withType(Jar).configureEach {
    BundleTaskExtension bundle = extensions.create(BundleTaskExtension.NAME, BundleTaskExtension.class, it)
    bundle.properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support
    bundle.bnd('Import-Package': 'com.example.util.*')

    doLast(bundle.buildAction())
}
"""

        file("src/main/java/com/example/main/Main.java") << """
package com.example.main;

import com.example.util.Util;

public class Main {
    public String getValue() {
        return "main " + Util.getUtil();
    }
}
"""

        and:
        file("direct/build.gradle") << """
import aQute.bnd.gradle.BundleTaskExtension

plugins {
    id "java-library"
}

group = "com.example.util"
version = "$directVersion"

// Replicate the JUnit 5 build, which lazily configures the Jar task to add the bundle extension and its action but does NOT apply any BND plugins
tasks.withType(Jar).configureEach {
    BundleTaskExtension bundle = extensions.create(BundleTaskExtension.NAME, BundleTaskExtension.class, it)
    bundle.properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support
    bundle.bnd("-exportcontents": "com.example.util.*")

    doLast(bundle.buildAction())
}
"""

        file("direct/src/main/java/com/example/util/Util.java") << """
package com.example.util;

public class Util {
    public static String getUtil() {
        return "util";
    }
}
"""

        when:
        runner("jar")
                .forwardOutput()
                .build()

        then: "version numbers exist in the manifest"
        assertJarManifestContains("Import-Package", "com.example.util;version=\"$calculatedDirectVersionRange\"")
    }

    def "BND plugin can resolve a bndrun file"() {
        given:
        def commonsVersion = "3.12.0"
        def directVersion = "2.7-SNAPSHOT"
        def calculatedDirectVersionRange = "[2.7,3)"
        def pathToBndrun = "src/main/resources/my.bndrun"

        settingsFile << """
include "direct"
"""
        buildFile << """
${addBNDBuilderPlugin()}

dependencies {
    implementation project(":direct")
}

tasks.named("jar") {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support

        bnd('Import-Package': 'com.example.util.*')
    }
}

tasks.register("resolve", aQute.bnd.gradle.Resolve) {
    bndrun = file("$pathToBndrun")
    outputBndrun = layout.buildDirectory.file("my.bndrun")
    bundles = configurations.bundles
    properties = ["project.osgiIdentity" : "org.apache.felix.eventadmin"]
}

configurations {
    bundles
}

dependencies {
    bundles 'org.apache.felix:org.apache.felix.framework:6.0.5'
    bundles 'org.apache.felix:org.apache.felix.eventadmin:1.4.6'
    bundles project(":direct")
}
"""

        file("src/main/java/com/example/Example.java") << """
package com.example;

import com.example.util.MyUtil;

public class Example {
    public boolean testIsEmpty(String someString) {
        return MyUtil.myIsEmpty(someString);
    }
}
"""

        file(pathToBndrun) << """
-runee: JavaSE-17
-runfw: org.apache.felix.framework;version='[6.0.5,6.0.5]'
-runrequires: osgi.identity;filter:='(osgi.identity=org.apache.felix.eventadmin)'
-runbundles: com.example.util;version=\\"${calculatedDirectVersionRange}\\"
"""

        and:
        file("direct/build.gradle") << """
plugins {
    id "java-library"
    id "biz.aQute.bnd.builder"
}

group = "com.example.direct"
version = "$directVersion"

${mavenCentralRepository()}

dependencies {
    api "org.apache.commons:commons-lang3:$commonsVersion"
}

jar {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support

        bnd("-exportcontents": "com.example.util.*")
    }
}
"""

        file("direct/src/main/java/com/example/util/MyUtil.java") << """
package com.example.util;

import org.apache.commons.lang3.StringUtils;

public class MyUtil {
    public static boolean myIsEmpty(String someString) {
        return StringUtils.isEmpty(someString);
    }
}
"""

        expect:
        runner(":resolve")
            .forwardOutput()
            .deprecations {
                expectAbstractTaskConventionDeprecationWarning {
                    causes = [null, "plugin 'biz.aQute.bnd.builder'"]
                }
            }
            .build()
    }

    @ToBeFixedForConfigurationCache(because = "Bndrun task does not support configuration cache")
    def "BND plugin can run a simple project"() {
        given:
        def pathToBndbnd = "bnd.bnd"
        def pathToBndrun = "my.bndrun"

        settingsFile << """
include "direct"
"""
        buildFile << """
${addBNDBuilderPlugin()}

dependencies {
    compileOnly 'org.osgi:osgi.core:5.0.0'
    runtimeOnly 'org.eclipse.platform:org.eclipse.osgi:3.18.100'
}

tasks.named("jar") {
    bundle {
        properties.empty() // Make this work with CC per: https://github.com/bndtools/bnd/blob/master/gradle-plugins/README.md#gradle-configuration-cache-support
    }
}

task run(type: aQute.bnd.gradle.Bndrun) {
    dependsOn tasks.named("jar")
    bndrun = file("${pathToBndrun}")
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(JavaVersion.current().getMajorVersion())
    }
}
"""

        file("src/main/java/com/example/Activator.java") << """
package com.example;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        System.err.println("Example project ran.");
        System.exit(0);
    }

    @Override
    public void stop(BundleContext context) throws Exception {}
}

"""

        file(pathToBndrun) << """
-runfw: org.eclipse.osgi
-runee: JavaSE-17

-runbundles: bnd-smoke-test

-runproperties:

-runtrace: true
"""

        file(pathToBndbnd) << """
-sources: true
Bundle-Activator: com.example.Activator
"""

        expect:
        def result = runner(":run")
            .forwardOutput()
            .deprecations {
                expectAbstractTaskConventionDeprecationWarning {
                    causes = [null, "plugin 'biz.aQute.bnd.builder'"]
                }
            }
            .build()

        assert result.getOutput().contains("Example project ran.")
    }

    private void assertJarManifestContains(String attribute, String value) {
        JarTestFixture jarTestFixture = new JarTestFixture(file("build/libs/bnd-smoke-test.jar"))
        assert jarTestFixture.manifest.mainAttributes.getValue(attribute) == value
    }

    private String addBNDBuilderPlugin() {
        return """
plugins {
    id "biz.aQute.bnd.builder"
}

${mavenCentralRepository()}
"""
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'biz.aQute.bnd.builder': Versions.of(TestedVersions.bnd),
        ]
    }
}
