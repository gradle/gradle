/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests.tooling.m9

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.Assume

@TargetGradleVersion(">=3.0")
class M9JavaConfigurabilityCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.requireDaemons()
    }

    def "uses defaults when a variant of empty jvm args requested"() {
        when:
        def env = withConnection {
            it.model(BuildEnvironment.class).setJvmArguments(new String[0]).get()
        }

        def env2 = withConnection {
            it.model(BuildEnvironment.class).setJvmArguments(null).get()
        }

        def env3 = withConnection {
            it.model(BuildEnvironment.class).get()
        }

        then:
        env.java.jvmArguments
        env.java.jvmArguments == env2.java.jvmArguments
        env.java.jvmArguments == env3.java.jvmArguments
    }

    def "customized java home is reflected in the java.home and the build model"() {
        def jdk = AvailableJavaHomes.getAvailableJdk { targetDist.isToolingApiTargetJvmSupported(it.languageVersion) }
        Assume.assumeNotNull(jdk)

        given:
        file('build.gradle') << "project.description = new File(System.getProperty('java.home')).canonicalPath"

        when:

        BuildEnvironment env
        GradleProject project
        withConnection {
            env = it.model(BuildEnvironment.class).setJavaHome(jdk.javaHome).get()
            project = it.model(GradleProject.class).setJavaHome(jdk.javaHome).get()
        }

        then:
        project.description.startsWith(env.java.javaHome.canonicalPath)
    }

    def "tooling api provided java home takes precedence over gradle.properties"() {
        File currentJavaHome = new File(System.getProperty("java.home")).canonicalFile
        def jdk = AvailableJavaHomes.getAvailableJdk { targetDist.isToolingApiTargetJvmSupported(it.languageVersion) && it.javaHome.toFile() != currentJavaHome }
        Assume.assumeNotNull(jdk)
        File javaHome = jdk.javaHome
        String javaHomePath = TextUtil.escapeString(javaHome.canonicalPath)
        String otherJavaPath = TextUtil.escapeString(currentJavaHome.canonicalPath)
        file('build.gradle') << "assert new File(System.getProperty('java.home')).canonicalPath.startsWith('$javaHomePath')"
        file('gradle.properties') << "org.gradle.java.home=$otherJavaPath"

        when:
        def env = withConnection {
            it.newBuild().setJavaHome(javaHome).run() //the assert
            it.model(BuildEnvironment.class)
                    .setJavaHome(javaHome)
                    .get()
        }

        then:
        env != null
        env.java.javaHome == javaHome
        env.java.javaHome != currentJavaHome
    }
}
