/*
 * Copyright 2014 the original author or authors.
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







package org.gradle.integtests.fixtures

import org.apache.commons.lang.StringUtils

import javax.annotation.Nullable

class TestDependency {

    final String group
    final String name
    final String version
    final TestDependency pointsTo

    TestDependency(String notation) {
        def n = notation.split("->")
        assert n.length <= 2

        def s = n[0].split(":")
        if (s.length == 3) {
            this.group = s[0]
            this.name = s[1]
            this.version = s[2]
        } else if (s.length == 2) {
            if (StringUtils.isNumeric(s[1])) {
                this.group = "org"
                this.name = s[0]
                this.version = s[1]
            } else {
                this.group = s[0]
                this.name = s[1]
                this.version = "1"
            }
        } else if (s.length == 1) {
            this.group = "org"
            this.name = s[0]
            this.version = "1"
        } else {
            throw new IllegalArgumentException("Unable to create test dependency for input '$notation'")
        }

        this.pointsTo = (n.length > 1)? new TestDependency(n[1]) : null
    }

    String getNotation() {
        "$group:$name:$version"
    }

    String getJarName() {
        "$name-${version}.jar"
    }

    @Nullable TestDependency getPointsTo() {
        this.pointsTo
    }

    @Nullable TestDependency getDependsOn() {
        this.pointsTo
    }
}
