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
package org.gradle.tooling.internal.consumer

import org.gradle.api.internal.AbstractClassPathProvider
import org.gradle.messaging.actor.ActorFactory
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import spock.lang.Specification
import java.util.regex.Pattern
import org.gradle.util.TemporaryFolder
import org.junit.Rule

class DefaultToolingImplementationLoaderTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    final Distribution distribution = Mock()

    def usesMetaInfServiceToDetermineFactoryImplementation() {
        given:
        def loader = new DefaultToolingImplementationLoader()
        distribution.toolingImplementationClasspath >> ([
                getToolingApiResourcesDir(),
                AbstractClassPathProvider.getClasspathForClass(TestConnection.class),
                AbstractClassPathProvider.getClasspathForClass(ActorFactory.class),
                AbstractClassPathProvider.getClasspathForClass(Logger.class),
                getVersionResourcesDir(),
                AbstractClassPathProvider.getClasspathForClass(GradleVersion.class)
        ] as Set)

        when:
        def factory = loader.create(distribution)

        then:
        factory.class != TestConnection.class
        factory.class.name == TestConnection.class.name
    }

    private getToolingApiResourcesDir() {
        tmpDir.file("META-INF/services/org.gradle.tooling.internal.protocol.ConnectionVersion4") << TestConnection.name
        return tmpDir.dir;
    }

    private getVersionResourcesDir() {
        return getResourcesDir("org/gradle/releases.xml")
    }

    private getResourcesDir(String name) {
        def resource = getClass().classLoader.getResource(name)
        assert resource
        assert resource.protocol == 'file'
        def dir = resource.path.replaceFirst(Pattern.quote(name), '')
        return new File(dir)
    }

    def failsWhenNoImplementationDeclared() {
        ClassLoader cl = new ClassLoader() {}
        def loader = new DefaultToolingImplementationLoader(cl)

        when:
        loader.create(distribution)

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The specified <dist-display-name> is not supported by this tooling API version (${GradleVersion.current().version}, protocol version 4)"
        _ * distribution.toolingImplementationClasspath >> ([] as Set)
        _ * distribution.displayName >> '<dist-display-name>'
    }
}