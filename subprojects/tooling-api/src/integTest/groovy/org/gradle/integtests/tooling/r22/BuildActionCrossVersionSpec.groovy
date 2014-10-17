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

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.fixtures.executer.GradleBackedArtifactBuilder
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.ProjectConnection
import spock.lang.Ignore

@ToolingApiVersion(">=1.8")
@Ignore
class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    @TargetGradleVersion(">=2.2")
    def "can change the implementation of an action"() {
        // Make sure we reuse the same daemon
        toolingApi.requireIsolatedDaemons()

        def workDir = temporaryFolder.file("work")
        def implJar = workDir.file("action-impl.jar")
        def builder = new GradleBackedArtifactBuilder(new InProcessGradleExecuter(dist, temporaryFolder), workDir)

        given:
        builder.sourceFile('ActionImpl.java') << """
public class ActionImpl implements ${BuildAction.name}<String> {
    public String execute(${BuildController.name} controller) {
        return "one";
    }
}
"""
        builder.buildJar(implJar)
        def cl1 = new URLClassLoader([implJar.toURI().toURL()] as URL[], getClass().classLoader)
        def action1 = cl1.loadClass("ActionImpl").newInstance()

        when:
        def result1 = withConnection { ProjectConnection connection ->
            connection.action(action1).run()
        }

        then:
        result1 == "one"

        when:
        // Discard the impl jar from the jvm's jar file cache
        forceJarClose(implJar)
        workDir.deleteDir()
        builder.sourceFile('ActionImpl2.java') << """
public class ActionImpl2 implements ${BuildAction.name}<String> {
    public String execute(${BuildController.name} controller) {
        return new InnerThing().value;
    }

    class InnerThing {
        String value = "new result";
    }
}
"""
        builder.buildJar(implJar)
        def cl2 = new URLClassLoader([implJar.toURI().toURL()] as URL[], getClass().classLoader)
        def action2 = cl2.loadClass("ActionImpl2").newInstance()

        def result2 = withConnection { ProjectConnection connection ->
            connection.action(action2).run()
        }

        then:
        result2 == "new result"
    }

    def forceJarClose(File jar) {
        def factory = new sun.net.www.protocol.jar.JarFileFactory()
        def file = factory.get(jar.toURI().toURL())
        factory.close(file)
    }
}
