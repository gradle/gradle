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


import org.gradle.launcher.env.LenientEnvHacker.EnvironmentProvider
import org.gradle.util.GUtil
import org.gradle.util.OperatingSystem
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/1/11
 */
class LenientEnvHackerTest extends Specification {

    public final @Rule TestName test = new TestName()
    public final @Rule TemporaryFolder temp = new TemporaryFolder()
    def hacker = new LenientEnvHacker()
    def preservedEnvironment
    def preservedWorkDir

    def setup() {
        preservedEnvironment = System.getenv()
        preservedWorkDir = hacker.getProcessDir()
    }

    def cleanup() {
        try {
            hacker.setenv(preservedEnvironment)
        } finally {
            hacker.setProcessDir(preservedWorkDir)
        }
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

     def "is case sensitive on windows"() {
        when:
        hacker.setenv(test.methodName, "one");

        then:
        "one" == System.getenv(test.methodName)
         if (OperatingSystem.current().isWindows()) {
            assert "one" == System.getenv(test.methodName.toUpperCase())
            assert "one" == System.getenv(test.methodName.toLowerCase())
         }
    }

    def "does not explode when local environment cannot be initialized"() {
        given:
        def provider = Mock(EnvironmentProvider)
        provider.environment >> { throw new RuntimeException("You are using some awkward OS we don't know how to handle!") }

        hacker = new LenientEnvHacker(provider)

        when:
        hacker.setenv(test.methodName, "bar")
        hacker.setenv(GUtil.map())
        hacker.processDir
        hacker.processDir = "foo"

        then:
        noExceptionThrown()
    }

    def "does not explode when local environment is unstable"() {
        given:
        def provider = Mock(EnvironmentProvider)
        def env = Mock(Environment)
        provider.environment >> { env }
        env._ >> { throw new RuntimeException("You are using some awkward OS we don't know how to handle!") }

        hacker = new LenientEnvHacker(provider)

        when:
        hacker.setenv(test.methodName, "bar")
        hacker.setenv(GUtil.map())
        hacker.processDir
        hacker.processDir = "foo"

        then:
        noExceptionThrown()
    }

    def "updates current work dir of the process"() {
        given:
        assert hacker.processDir != temp.dir.absolutePath

        when:
        hacker.processDir = temp.dir.absolutePath

        then:
        hacker.processDir == temp.dir.absolutePath
    }
}
