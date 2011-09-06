/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.env;


import org.gradle.util.GUtil
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/1/11
 */
class LenientEnvHackerTest extends Specification {

    public final @Rule TestName test = new TestName()
    def hacker = new LenientEnvHacker()
    def preservedEnvironment

    def setup() {
        preservedEnvironment = System.getenv()
    }

    def cleanup() {
        hacker.setenv(preservedEnvironment)
    }

    def "added env is available explicitly"() {
        when:
        hacker.setenv(test.methodName, "bar")

        then:
        "bar" == System.getenv(test.methodName)
    }

    def "added env is available in envs map"() {
        when:
        hacker.setenv(test.methodName, "bar")

        then:
        "bar" == System.getenv()[test.methodName]
    }

    def "updated env is available explicitly"() {
        when:
        hacker.setenv(test.methodName, "one")
        hacker.setenv(test.methodName, "two")

        then:
        "two" == System.getenv(test.methodName)
    }

    def "updated env is available in envs map"() {
        when:
        hacker.setenv(test.methodName, "one")
        hacker.setenv(test.methodName, "two")

        then:
        "two" == System.getenv()[test.methodName]
    }

    def "updates multiple env variables"() {
        when:
        hacker.setenv(GUtil.map(test.methodName + 1, "one", test.methodName + 2, "two"));

        then:
        "one" == System.getenv()[test.methodName + 1]
        "two" == System.getenv(test.methodName + 2)
    }

    def "replaces existing env variables"() {
        when:
        hacker.setenv(test.methodName + 1, "one");

        then:
        "one" == System.getenv(test.methodName + 1)

        when:
        hacker.setenv(GUtil.map(test.methodName + 2, "two"));

        then:
        "two" == System.getenv(test.methodName + 2)
        null == System.getenv(test.methodName + 1)
    }

    def "does not explode when local environment is unfriendly"() {
        given:
        def env = Mock(Environment)
        env._ >> { throw new RuntimeException("You are using some awkward OS we don't know how to handle!") }

        hacker = new LenientEnvHacker(env)

        when:
        hacker.setenv("foo", "bar")

        then:
        noExceptionThrown()
    }
}
