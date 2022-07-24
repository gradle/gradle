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
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.ProjectConnection

import java.nio.file.Files

class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    def "can change the implementation of an action"() {
        // Make sure we reuse the same daemon
        toolingApi.requireIsolatedDaemons()

        disableJarCachingWhenUsingOldGradleVersion()

        def workDir = temporaryFolder.file("work")
        def implJar = workDir.file("action-impl.jar")
        def builder = new GradleBackedArtifactBuilder(new NoDaemonGradleExecuter(dist, temporaryFolder).withWarningMode(null), workDir)

        given:
        builder.sourceFile('ActionImpl.java') << """
public class ActionImpl implements ${BuildAction.name}<java.io.File> {
    public java.io.File execute(${BuildController.name} controller) {
        try {
            return new java.io.File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
"""
        builder.buildJar(implJar)

        def cl1 = new URLClassLoader([implJar.toURI().toURL()] as URL[], getClass().classLoader)
        def action1 = cl1.loadClass("ActionImpl").getConstructor().newInstance()

        when:
        File actualJar1 = withConnection { ProjectConnection connection ->
            connection.action(action1).run()
        }
        cl1.close()
        Files.delete(implJar.toPath())

        then:
        actualJar1 != implJar
        actualJar1.name == implJar.name

        when:
        builder.sourceFile('ActionImpl.java').text = """
public class ActionImpl implements ${BuildAction.name}<String> {
    public String execute(${BuildController.name} controller) {
        return getClass().getProtectionDomain().getCodeSource().getLocation().toString();
    }
}
"""
        builder.buildJar(implJar)
        def cl2 = new URLClassLoader([implJar.toURI().toURL()] as URL[], getClass().classLoader)
        def action2 = cl2.loadClass("ActionImpl").getConstructor().newInstance()

        String result2 = withConnection { ProjectConnection connection ->
            connection.action(action2).run()
        }
        cl2.close()
        Files.delete(implJar.toPath())

        then:
        def actualJar2 = new File(new URI(result2))
        actualJar2 != implJar
        actualJar2 != actualJar1
        actualJar2.name == implJar.name

        cleanup:
        cl1?.close()
        cl2?.close()
    }

    private void disableJarCachingWhenUsingOldGradleVersion() {
        if (targetDist.toolingApiLocksBuildActionClasses) {
            // Tooling api providers from older Gradle would use the Jar URL cache, leaving Jar files open. Disable URL caching for these versions
            // sun.net.www.protocol.jar.JarURLConnection leaves the JarFile instance open if URLConnection caching is enabled.
            new URL("jar:file://valid_jar_url_syntax.jar!/").openConnection().setDefaultUseCaches(false)
        }
    }
}
