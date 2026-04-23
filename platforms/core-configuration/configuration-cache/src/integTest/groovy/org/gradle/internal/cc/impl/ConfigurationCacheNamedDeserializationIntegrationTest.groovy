/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

class ConfigurationCacheNamedDeserializationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    @Issue("https://github.com/gradle/gradle/issues/36718")
    def "can load configuration cache with generated Named instance after daemon restart"() {
        given:
        file("buildSrc/src/main/java/my/MyNamed.java") << """
            package my;
            import org.gradle.api.Named;
            public interface MyNamed extends Named {}
        """

        buildFile """
            import my.MyNamed

            abstract class ShowNamed extends DefaultTask {
                @Internal
                MyNamed value

                @TaskAction
                void show() {
                    println("name = " + value.name)
                }
            }

            tasks.register("show", ShowNamed) {
                value = objects.named(MyNamed, "foo")
            }
        """
        def configurationCache = new ConfigurationCacheFixture(this)

        when:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateStored()
        outputContains("name = foo")

        when:
        stopDaemons()

        and:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateLoaded()
        outputContains("name = foo")
    }

    @Issue("https://github.com/gradle/gradle/issues/36718")
    def "can load configuration cache with generated abstract-class Named instance after daemon restart"() {
        given:
        file("buildSrc/src/main/java/my/MyNamed.java") << """
            package my;
            import org.gradle.api.Named;
            public abstract class MyNamed implements Named {
                public String getPrefixed() { return "X-" + getName(); }
            }
        """

        buildFile """
            import my.MyNamed

            abstract class ShowNamed extends DefaultTask {
                @Internal
                MyNamed value

                @TaskAction
                void show() {
                    println("name = " + value.name)
                    println("prefixed = " + value.prefixed)
                }
            }

            tasks.register("show", ShowNamed) {
                value = objects.named(MyNamed, "foo")
            }
        """
        def configurationCache = new ConfigurationCacheFixture(this)

        when:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateStored()
        outputContains("name = foo")
        outputContains("prefixed = X-foo")

        when:
        stopDaemons()

        and:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateLoaded()
        outputContains("name = foo")
        outputContains("prefixed = X-foo")
    }

    def "can load configuration cache with decorated Managed+GeneratedSubclass instance after daemon restart"() {
        given:
        file("buildSrc/src/main/java/my/Thing.java") << """
            package my;
            import org.gradle.api.provider.Property;
            public abstract class Thing {
                public abstract Property<String> getFoo();
            }
        """

        buildFile """
            import my.Thing

            abstract class ShowThing extends DefaultTask {
                @Internal
                Thing value

                @TaskAction
                void show() {
                    println("foo = " + value.foo.get())
                }
            }

            tasks.register("show", ShowThing) {
                value = objects.newInstance(Thing)
                value.foo.set("baz")
            }
        """
        def configurationCache = new ConfigurationCacheFixture(this)

        when:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = baz")

        when:
        stopDaemons()

        and:
        configurationCacheRun("show")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo = baz")
    }

    void stopDaemons() {
        executer.withArguments("--stop", "--info").run()
    }
}
