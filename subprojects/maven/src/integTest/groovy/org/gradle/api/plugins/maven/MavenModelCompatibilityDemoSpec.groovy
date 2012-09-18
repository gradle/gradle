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



package org.gradle.api.plugins.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import org.gradle.mvn3.org.apache.maven.model.Model as ModelFromNewMaven3Classes
import org.apache.maven.model.Model as ModelFromAntTasks

/**
 * by Szczepan Faber, created at: 9/4/12
 */
class MavenModelCompatibilityDemoSpec extends AbstractIntegrationSpec {

    def "compare"() {
        expect:
        //This is not really a test.
        // It is a demonstration of compatibility between classes in old model (that we want to remove)
        // and classes in the new model.
        //The check is quite naive, it uses of the Class.getMethods() so it excludes non-public methods and fields.
        // it recursively checks encountered types if they are found in method signatures or return types.

        typesMatch(ModelFromAntTasks, ModelFromNewMaven3Classes, new HashSet())
    }

    void typesMatch(Class old, Class candidate, Set completed) {
        if (!completed.add(old.simpleName)) {
            return
        }
        println "checking: $old.simpleName VS $candidate.simpleName"
        old.methods.each { m ->
            def found = candidate.methods.find { it.name == m.name && it.parameterTypes*.simpleName == m.parameterTypes*.simpleName }
            if (!found) {
                println " only in $old - $m.name"
            } else {
                if (found.returnType != m.returnType) {
                    typesMatch(m.returnType, found.returnType, completed)
                }
                if (found.parameterTypes != m.parameterTypes) {
                    found.parameterTypes.size().times { i ->
                        typesMatch(m.parameterTypes[i], found.parameterTypes[i], completed)
                    }
                }
            }
        }
    }
}