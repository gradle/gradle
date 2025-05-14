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

package org.gradle.testing.junit.platform

import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_PLATFORM_VERSION

/**
 * Tests JUnitPlatform integrations with {@code LauncherSessionListener}.
 */
class JUnitPlatformLauncherSessionListenerIntegrationTest extends JUnitPlatformIntegrationSpec {

    /**
     * @see <a href=https://github.com/JetBrains/intellij-community/commit/d41841670c8a98c0464ef25ef490c79b5bafe8a9">The IntelliJ commit</a>
     * which introduced a {@code LauncherSessionListener} onto the test classpath when using the {@code org.jetbrains.intellij} plugin.
     */
    @Issue("https://github.com/gradle/gradle/issues/22333")
    def "LauncherSessionListeners can be implemented and are loaded by junit"() {
        settingsFile << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("org.junit.platform:junit-platform-launcher:1.10.0")
            }
        """
        file("other/src/main/java/com/example/MyLauncherSessionListener.java") << """
            package com.example;
            import org.junit.platform.launcher.LauncherSession;
            import org.junit.platform.launcher.LauncherSessionListener;
            public class MyLauncherSessionListener implements LauncherSessionListener {
                public void launcherSessionOpened(LauncherSession session) {
                    System.out.println("Session opened");
                }
                public void launcherSessionClosed(LauncherSession session) {
                    System.out.println("Session closed");
                }
            }
        """
        file("other/src/main/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener") << """
            com.example.MyLauncherSessionListener
        """

        buildFile.text = """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}")

                testRuntimeOnly(project(':other'))
            }

            test {
                useJUnitPlatform()
                testLogging.showStandardStreams = true
            }
        """
        file("src/test/java/com/example/MyTest.java") << """
            package com.example;

            public class MyTest {
                @org.junit.jupiter.api.Test
                public void myTest() { }
            }
        """

        when:
        succeeds "test"

        then:
        outputContains("Session opened")
        outputContains("Session closed")
    }

    def "creates LauncherSession before loading test classes"() {
        given:
        createSimpleJupiterTest()
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-launcher:${LATEST_PLATFORM_VERSION}'
            }
            test {
                testLogging {
                    showStandardStreams = true
                }
            }
        """
        file('src/test/java/NoisyLauncherSessionListener.java') << '''
            import org.junit.platform.launcher.*;
            public class NoisyLauncherSessionListener implements LauncherSessionListener {
                @Override public void launcherSessionOpened(LauncherSession session) {
                    System.out.println("launcherSessionOpened");
                    Thread thread = Thread.currentThread();
                    ClassLoader parent = thread.getContextClassLoader();
                    ClassLoader replacement = new ClassLoader(parent) {
                        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                            System.out.println("Loading class " + name);
                            return super.loadClass(name, resolve);
                        }
                    };
                    thread.setContextClassLoader(replacement);
                }
                @Override public void launcherSessionClosed(LauncherSession session) {
                    System.out.println("launcherSessionClosed");
                }
            }
        '''
        file('src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener') << '''\
            NoisyLauncherSessionListener
        '''.stripIndent(true)

        expect:
        succeeds('test')
        outputContains('launcherSessionOpened')
        outputContains('Loading class org.gradle.JUnitJupiterTest')
        outputContains('launcherSessionClosed')
    }
}
