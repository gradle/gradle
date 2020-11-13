/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.std

import com.google.common.collect.Interners
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Supplier

class DefaultVersionCatalogBuilderTest extends Specification {

    @Subject
    DefaultVersionCatalogBuilder builder = new DefaultVersionCatalogBuilder("libs", Interners.newStrongInterner(), Interners.newStrongInterner(), TestUtil.objectFactory(), TestUtil.providerFactory(), Stub(PluginDependenciesSpec), Stub(Supplier))

    @Unroll("#notation is an invalid notation")
    def "reasonable error message if notation is invalid"() {
        when:
        builder.alias("foo").to(notation)

        then:
        InvalidUserDataException ex = thrown()
        ex.message == 'Invalid dependency notation: it must consist of 3 parts separated by colons, eg: my.group:artifact:1.2'

        where:
        notation << ["", "a", "a:", "a:b", ":b", "a:b:", ":::", "a:b:c:d"]
    }

    @Unroll("#notation is an invalid alias")
    def "reasonable error message if alias is invalid"() {
        when:
        builder.alias(notation).to("org:foo:1.0")

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Invalid alias name '$notation': it must match the following regular expression: [a-z]([a-zA-Z0-9_.\\-])+"

        where:
        notation << ["", "a", "1a", "A", "Aa", "abc\$", "abc&"]
    }

    @Unroll
    def "forbids using #name as a dependency alias"() {
        when:
        builder.alias(name).to("org:foo:1.0")

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Invalid alias name '$name': it must not end with '$suffix'"

        where:
        name          | suffix
        "bundles"     | "bundles"
        "versions"    | "versions"
        "fooBundle"   | "bundle"
        "fooVersion"  | "version"
        "foo.bundle"  | "bundle"
        "foo.version" | "version"
    }

    @Unroll("#notation is an invalid bundle name")
    def "reasonable error message if bundle name is invalid"() {
        when:
        builder.bundle(notation, [])

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Invalid bundle name '$notation': it must match the following regular expression: [a-z]([a-zA-Z0-9_.\\-])+"

        where:
        notation << ["", "a", "1a", "A", "Aa", "abc\$", "abc&"]
    }

    def "warns if multiple entries use the same alias"() {
        StandardOutputListener listener = Mock()
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        builder.alias("foo").to("a:b:1.0")

        when:
        builder.alias("foo").to("e:f:1.1")

        then:
        1 * listener.onOutput("Duplicate entry for alias 'foo': dependency {group='a', name='b', version='1.0'} is replaced with dependency {group='e', name='f', version='1.1'}")

        cleanup:
        loggingManager.stop()
    }

    def "warns if multiple entries use the same bundle name"() {
        StandardOutputListener listener = Mock()
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        builder.bundle("foo", ["a", "b"])

        when:
        builder.bundle("foo", ["c", "d", "e"])

        then:
        1 * listener.onOutput("Duplicate entry for bundle 'foo': [a, b] is replaced with [c, d, e]")

        cleanup:
        loggingManager.stop()
    }

    def "fails building the model if a bundle references a non-existing alias"() {
        builder.alias("guava").to("com.google.guava:guava:17.0")
        builder.bundle("toto", ["foo"])

        when:
        builder.build()

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "A bundle with name 'toto' declares a dependency on 'foo' which doesn't exist"
    }

    def "model reflects what is declared"() {
        builder.alias("guava").to("com.google.guava:guava:17.0")
        builder.alias("groovy").to("org.codehaus.groovy", "groovy").version {
            it.strictly("3.0.5")
        }
        builder.alias("groovy-json").to("org.codehaus.groovy", "groovy-json").version {
            it.prefer("3.0.5")
        }
        builder.bundle("groovy", ["groovy", "groovy-json"])

        when:
        def model = builder.build()

        then:
        model.bundleAliases == ["groovy"]
        model.getBundle("groovy").components == ["groovy", "groovy-json"]

        model.dependencyAliases == ["groovy", "groovy-json", "guava"]
        model.getDependencyData("guava").version.requiredVersion == '17.0'
        model.getDependencyData("groovy").version.strictVersion == '3.0.5'
        model.getDependencyData("groovy-json").version.strictVersion == ''
        model.getDependencyData("groovy-json").version.preferredVersion == '3.0.5'
    }

    def "can use rich versions in short-hand notation"() {
        builder.alias("dummy").to("g:a:1.5!!")
        builder.alias("alias").to("g:a:[1.0,2.0[!!1.7")

        when:
        def model = builder.build()

        then:
        model.dependencyAliases == ["alias", "dummy"]
        model.getDependencyData("dummy").version.strictVersion == '1.5'
        model.getDependencyData("alias").version.strictVersion == '[1.0,2.0['
        model.getDependencyData("alias").version.preferredVersion == '1.7'
    }

    def "strings are interned"() {
        builder.alias("foo").to("bar", "baz").version {
            it.require "1.0"
        }
        builder.alias("baz").to("foo", "bar").version {
            it.prefer "1.0"
        }
        when:
        def model = builder.build()

        then:
        def bazKey = model.dependencyAliases.find { it == 'baz' }
        model.getDependencyData("foo").group.is(model.getDependencyData("baz").name)
        model.getDependencyData("foo").name.is(bazKey)
        model.getDependencyData("foo").version.requiredVersion.is(model.getDependencyData("baz").version.preferredVersion)
    }

    def "can create an alias to a referenced version"() {
        builder.version("ver", "1.7!!")
        builder.alias("foo").to("org", "foo").versionRef("ver")

        when:
        def model = builder.build()

        then:
        model.getDependencyData("foo").version.strictVersion == "1.7"
    }

    def "can create an alias with an empty version"() {
        builder.alias("foo").to("org", "foo").withoutVersion()

        when:
        def model = builder.build()

        then:
        model.getDependencyData("foo").version.requiredVersion == ""
    }

    def "reasonable error message if referenced version doesn't exist"() {
        builder.alias("foo").to("org", "foo").versionRef("nope")

        when:
        builder.build()

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Referenced version 'nope' doesn't exist on dependency org:foo"
    }
}
