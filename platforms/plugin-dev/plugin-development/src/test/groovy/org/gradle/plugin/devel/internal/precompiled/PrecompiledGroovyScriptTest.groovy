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

package org.gradle.plugin.devel.internal.precompiled

import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.plugin.internal.InvalidPluginIdException
import spock.lang.Specification

class PrecompiledGroovyScriptTest extends Specification {
    private TextFileResourceLoader loader = Mock()

    def "throws for filename yielding invalid plugin id"() {
        when:
        new PrecompiledGroovyScript(new File("/foo/bar/f%izzbuzz.gradle"), loader)

        then:
        thrown InvalidPluginIdException
    }

    def "creates valid java classname from script filename based plugin id"() {
        expect:
        def script = new PrecompiledGroovyScript(new File("/foo/bar/$filename"), loader)
        script.pluginAdapterClassName == javaClass

        where:
        filename                               | javaClass
        "foo.gradle"                           | "FooPlugin"
        "foo.bar.gradle"                       | "FooBarPlugin"
        "test-plugin.gradle"                   | "TestPluginPlugin"
        "foo.bar.test-plugin.gradle"           | "FooBarTestPluginPlugin"
        "foo.bar.-foo.gradle"                  | "FooBarFooPlugin"
        "foo.bar._foo.gradle"                  | "FooBar_fooPlugin"
        "123.gradle"                           | "_123Plugin"
        "dev.gradleplugins.some-plugin.gradle" | "DevGradlepluginsSomePluginPlugin"
    }
}
