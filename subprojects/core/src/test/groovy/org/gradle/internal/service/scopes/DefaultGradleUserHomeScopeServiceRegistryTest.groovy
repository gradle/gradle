/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.internal.service.DefaultServiceRegistry
import spock.lang.Specification

class DefaultGradleUserHomeScopeServiceRegistryTest extends Specification {
    def globalServices = DefaultServiceRegistry.create(new GlobalServiceProvider())
    def homeDirServices = new DefaultGradleUserHomeScopeServiceRegistry(globalServices, new HomeDirServiceProvider())

    def "creates service registry contextualised to home dir"() {
        def dir = new File("home-dir")

        expect:
        def services = homeDirServices.getServicesFor(dir)
        services.get(SomeGlobalService) != null
        services.get(SomeHomeDirService).homeDir == dir
    }

    def "reuses service registry when home dir is the same as last use"() {
        def dir = new File("home-dir")

        given:
        def servicesBefore = homeDirServices.getServicesFor(dir)
        def globalService = servicesBefore.get(SomeGlobalService)
        def homeDirService = servicesBefore.get(SomeHomeDirService)
        homeDirServices.release(servicesBefore)

        expect:
        def services = homeDirServices.getServicesFor(dir)
        services.get(SomeGlobalService).is(globalService)
        services.get(SomeHomeDirService).is(homeDirService)
        services.get(SomeHomeDirService).homeDir == dir
        !services.get(SomeHomeDirService).closed
    }

    def "does not close services when registry is released"() {
        def dir = new File("home-dir")

        given:
        def services = homeDirServices.getServicesFor(dir)
        def homeDirService = services.get(SomeHomeDirService)
        homeDirServices.release(services)

        expect:
        !homeDirService.closed
    }

    def "closes and recreates services when home dir is different to last use"() {
        def dir1 = new File("home-dir-1")
        def dir2 = new File("home-dir-2")

        given:
        def servicesBefore = homeDirServices.getServicesFor(dir1)
        def globalService = servicesBefore.get(SomeGlobalService)
        def homeDirService = servicesBefore.get(SomeHomeDirService)
        homeDirServices.release(servicesBefore)

        expect:
        def services = homeDirServices.getServicesFor(dir2)
        services.get(SomeGlobalService).is(globalService)
        !services.get(SomeHomeDirService).is(homeDirService)
        services.get(SomeHomeDirService).homeDir == dir2
        homeDirService.closed
    }

    def "closes services when registry closed"() {
        def dir = new File("home-dir")

        given:
        def services = homeDirServices.getServicesFor(dir)
        def homeDirService = services.get(SomeHomeDirService)
        homeDirServices.release(services)

        when:
        homeDirServices.close()

        then:
        homeDirService.closed
    }

    def "fails when previous services not released"() {
        def dir = new File("home-dir")

        given:
        homeDirServices.getServicesFor(dir)

        when:
        homeDirServices.getServicesFor(dir)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Gradle user home directory scoped services have not been released.'
    }

    def "fails when services already released"() {
        def dir = new File("home-dir")

        given:
        def services = homeDirServices.getServicesFor(dir)
        homeDirServices.release(services)

        when:
        homeDirServices.release(services)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Gradle user home directory scoped services have already been released.'
    }

    def "close fails when services not released"() {
        def dir = new File("home-dir")

        given:
        homeDirServices.getServicesFor(dir)

        when:
        homeDirServices.close()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Gradle user home directory scoped services have not been released.'
    }

    class SomeGlobalService {
    }

    class GlobalServiceProvider {
        SomeGlobalService createGlobalService() {
            return new SomeGlobalService()
        }
    }

    class SomeHomeDirService implements Closeable {
        final File homeDir;
        boolean closed

        SomeHomeDirService(File homeDir) {
            this.homeDir = homeDir
        }

        @Override
        void close() throws IOException {
            closed = true
        }
    }

    class HomeDirServiceProvider {
        SomeHomeDirService createService(File homeDir) {
            return new SomeHomeDirService(homeDir)
        }
    }
}
