/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.groovy.scripts

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ClosureTakingMethodsIntegrationTest extends AbstractIntegrationSpec {
    @NotYetImplemented
    def 'can use Action-taking methods in place of Closures with @CompileStatic'() {
        def myPlugin = file("buildSrc/src/main/groovy/com/example/MyPlugin.groovy")
        myPlugin.groovy """
package com.example

import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.internal.ConfigureUtil

import javax.inject.Inject

@CompileStatic
abstract class MyPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        MyExt myext = project.extensions.create("myext", MyExt, project)
        myext.method {
            assert it instanceof Project
            description = "Set from plugin"
        }
    }
}

@CompileStatic
abstract class MyExt {
    public String sensed = null

    private final Project project

    @Inject
    MyExt(Project project) {
        this.project = project
    }

    void method(@DelegatesTo(Project.class) Closure<?> closure) {
        method(ConfigureUtil.configureUsing(closure))
        sensed = "closure"
    }

    void method(Action<? super Project> action) {
        action.execute(project)
        sensed = "action"
    }
}
"""
        file("buildSrc/build.gradle") << """
plugins {
    id 'groovy-gradle-plugin'
}

gradlePlugin {
    plugins {
        myPlugin {
            id = 'com.example.myPlugin'
            implementationClass = 'com.example.MyPlugin'
        }
    }
}
"""

        buildFile << """
plugins {
    id 'com.example.myPlugin'
}

assert description == "Set from plugin"
assert myext.sensed == "closure"
"""
        when:
        succeeds("help")
        then:
        noExceptionThrown()

        when:
        // Remove closure taking method
        myPlugin.replace("""
    void method(@DelegatesTo(Project.class) Closure<?> closure) {
        method(ConfigureUtil.configureUsing(closure))
        sensed = "closure"
    }
""", "")
        buildFile << """
plugins {
    id 'com.example.myPlugin'
}

assert description == "Set from plugin"
assert myext.sensed == "action"
"""
        then:
        succeeds("help")
    }
}
