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

import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import spock.lang.Specification

class DefaultGradleUserHomeScopeServiceRegistryTest extends Specification {
    def globalServices = DefaultServiceRegistry.create(new GlobalServiceProvider())
    def homeDirServices = new DefaultGradleUserHomeScopeServiceRegistry(globalServices, new HomeDirServiceProvider())

    def "creates service registry contextualised to home dir"() {
        def dir = new File("home-dir")

        expect:
        def services = homeDirServices.getServicesFor(dir)
        services.get(SomeGlobalService) != null
        services.get(GradleUserHomeDirProvider).gradleUserHomeDirectory == dir
        services.get(SomeHomeDirService).homeDir == dir
    }

    def "reuses service registry when home dir is the same as last use"() {
        def dir = new File("home-dir")

        given:
        def servicesBefore = homeDirServices.getServicesFor(dir)
        def globalService = servicesBefore.get(SomeGlobalService)
        def userHomeDirProvider = servicesBefore.get(GradleUserHomeDirProvider)
        def homeDirService = servicesBefore.get(SomeHomeDirService)
        homeDirServices.release(servicesBefore)

        expect:
        def services = homeDirServices.getServicesFor(dir)
        services.get(SomeGlobalService).is(globalService)
        services.get(GradleUserHomeDirProvider).is(userHomeDirProvider)
        services.get(SomeHomeDirService).is(homeDirService)
        services.get(SomeHomeDirService).homeDir == dir
        !services.get(SomeHomeDirService).closed
    }

    def "reuses service registry when services for home dir already in use"() {
        def dir = new File("home-dir")

        given:
        def servicesBefore = homeDirServices.getServicesFor(dir)
        def globalService = servicesBefore.get(SomeGlobalService)
        def userHomeDirProvider = servicesBefore.get(GradleUserHomeDirProvider)
        def homeDirService = servicesBefore.get(SomeHomeDirService)

        expect:
        def services = homeDirServices.getServicesFor(dir)
        services.get(SomeGlobalService).is(globalService)
        services.get(GradleUserHomeDirProvider).is(userHomeDirProvider)
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
        def userHomeDirProvider = servicesBefore.get(GradleUserHomeDirProvider)
        def homeDirService = servicesBefore.get(SomeHomeDirService)
        homeDirServices.release(servicesBefore)

        expect:
        def services = homeDirServices.getServicesFor(dir2)
        services.get(SomeGlobalService).is(globalService)
        !services.get(GradleUserHomeDirProvider).is(userHomeDirProvider)
        services.get(GradleUserHomeDirProvider).gradleUserHomeDirectory == dir2
        !services.get(SomeHomeDirService).is(homeDirService)
        services.get(SomeHomeDirService).homeDir == dir2
        homeDirService.closed
    }

    def "creates new services when home dir is different to home dir currently in use"() {
        def dir1 = new File("home-dir-1")
        def dir2 = new File("home-dir-2")

        given:
        def servicesBefore = homeDirServices.getServicesFor(dir1)
        def globalService = servicesBefore.get(SomeGlobalService)
        def userHomeDirProvider = servicesBefore.get(GradleUserHomeDirProvider)
        def homeDirService = servicesBefore.get(SomeHomeDirService)

        expect:
        def services = homeDirServices.getServicesFor(dir2)
        services.get(SomeGlobalService).is(globalService)
        !services.get(GradleUserHomeDirProvider).is(userHomeDirProvider)
        services.get(GradleUserHomeDirProvider).gradleUserHomeDirectory == dir2
        !services.get(SomeHomeDirService).is(homeDirService)
        services.get(SomeHomeDirService).homeDir == dir2

        and:
        !homeDirService.closed
    }

    def "closes services for home dir when another home dir is in use"() {
        def dir1 = new File("home-dir-1")
        def dir2 = new File("home-dir-2")

        given:
        def servicesHomeDir1 = homeDirServices.getServicesFor(dir1)
        def homeDir1Service = servicesHomeDir1.get(SomeHomeDirService)

        when:
        def services1 = homeDirServices.getServicesFor(dir2)
        def services2 = homeDirServices.getServicesFor(dir2)
        def homeDir2Service = services2.get(SomeHomeDirService)
        homeDirServices.release(services1)

        then:
        !homeDir2Service.closed
        !homeDir1Service.closed

        when:
        homeDirServices.release(services2)

        then:
        homeDir2Service.closed
        !homeDir1Service.closed
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

    def "getCurrentServices returns empty when no services created"() {
        expect:
        !homeDirServices.getCurrentServices().present
    }

    def "getCurrentServices returns current service registry when available"() {
        def dir = new File("home-dir")

        when:
        def services = homeDirServices.getServicesFor(dir)

        then:
        homeDirServices.getCurrentServices().get().is(services)
    }

    def "releases services when reuse system property is false"() {
        def dir = new File("home-dir")
        System.setProperty(DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES, "false")

        given:
        def services = homeDirServices.getServicesFor(dir)
        def homeDirService = services.get(SomeHomeDirService)

        when: "release is called"
        homeDirServices.release(services)

        then: "service should be closed and removed"
        homeDirService.closed
        !homeDirServices.@servicesForHomeDir.containsKey(dir)

        cleanup:
        System.clearProperty(DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES)
    }

    def "closes and removes old home dir when switching to a new one"() {
        def dir1 = new File("home-dir-1")
        def dir2 = new File("home-dir-2")

        given:
        def services1 = homeDirServices.getServicesFor(dir1)
        def homeDirService1 = services1.get(SomeHomeDirService)
        homeDirServices.release(services1)

        when:
        def services2 = homeDirServices.getServicesFor(dir2)
        def homeDirService2 = services2.get(SomeHomeDirService)

        then:
        homeDirService1.closed
        homeDirServices.@servicesForHomeDir.containsKey(dir2)
        !homeDirService2.closed
    }

    def "close clears all cached services"() {
        def dir1 = new File("home-dir-1")
        def dir2 = new File("home-dir-2")

        given:
        def services1 = homeDirServices.getServicesFor(dir1)
        def services2 = homeDirServices.getServicesFor(dir2)
        homeDirServices.release(services1)
        homeDirServices.release(services2)

        when:
        homeDirServices.close()

        then:
        homeDirServices.@servicesForHomeDir.isEmpty()
    }

    def "multiple acquires and releases leave services cached when count is zero"() {
        def dir = new File("home-dir")

        when:
        def s1 = homeDirServices.getServicesFor(dir)
        def s2 = homeDirServices.getServicesFor(dir)
        homeDirServices.release(s1)
        homeDirServices.release(s2)

        then: "services remain cached with count 0"
        homeDirServices.@servicesForHomeDir[dir].count == 0
    }

    def "count gets decreased when attempting to release already released services"() {
        def dir = new File("home-dir")

        given:
        def services = homeDirServices.getServicesFor(dir)
        def initialCount = homeDirServices.@servicesForHomeDir[dir].count

        when: "services are released for the first time"
        homeDirServices.release(services)
        def countAfterFirstRelease = homeDirServices.@servicesForHomeDir[dir].count

        and: "attempting to release the same services again"
        try {
            homeDirServices.release(services)
        } catch (IllegalStateException e) {
            assert e.message == 'Gradle user home directory scoped services have already been released.'
        }

        then:
        def countAfterSecondRelease = homeDirServices.@servicesForHomeDir[dir].count

        and: "count keeps decrementing despite the exception"
        initialCount == 1
        countAfterFirstRelease == 0
        countAfterSecondRelease == 0
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
        e.message == "Services for Gradle user home directory 'home-dir' have not been released."
    }

    def "increments and decrements service usage count explicitly"() {
        def dir = new File("home-dir")

        when: "first time services are requested"
        def services1 = homeDirServices.getServicesFor(dir)

        then: "count is incremented to 1"
        def internalServices = homeDirServices.@servicesForHomeDir[dir]
        internalServices.count == 1

        when: "same home dir is requested again"
        def services2 = homeDirServices.getServicesFor(dir)

        then: "count is incremented to 2"
        homeDirServices.@servicesForHomeDir[dir].count == 2

        when: "one release happens"
        homeDirServices.release(services1)

        then: "count is decremented to 1"
        homeDirServices.@servicesForHomeDir[dir].count == 1

        when: "second release happens"
        homeDirServices.release(services2)

        then: "count is decremented to 0 and services remain cached"
        !homeDirServices.@servicesForHomeDir.isEmpty()
        homeDirServices.@servicesForHomeDir[dir].count == 0
    }

    def "count is properly incremented and decremented when services are acquired and released"() {
        def dir = new File("home-dir")

        when: "services are first acquired"
        def services1 = homeDirServices.getServicesFor(dir)

        then: "count should be 1"
        // Access internal state through reflection to verify count
        def servicesField = homeDirServices.getClass().getDeclaredField("servicesForHomeDir")
        servicesField.setAccessible(true)
        def servicesMap = servicesField.get(homeDirServices) as Map<File, DefaultGradleUserHomeScopeServiceRegistry.Services>
        servicesMap.size() == 1
        servicesMap.get(dir).count == 1

        when: "same services are acquired again"
        def services2 = homeDirServices.getServicesFor(dir)

        then: "count should be 2"
        servicesMap.get(dir).count == 2

        when: "first instance is released"
        homeDirServices.release(services1)

        then: "count should be 1"
        servicesMap.get(dir).count == 1

        when: "second instance is released"
        homeDirServices.release(services2)

        then: "count should be 0 but services are still cached"
        servicesMap.get(dir).count == 0
        servicesMap.size() == 1 // Services still cached for reuse
    }

    class SomeGlobalService {
    }

    class GlobalServiceProvider implements ServiceRegistrationProvider {
        @Provides
        SomeGlobalService createGlobalService() {
            return new SomeGlobalService()
        }
    }

    @ServiceScope(Scope.UserHome.class)
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

    class HomeDirServiceProvider implements ServiceRegistrationProvider {
        @Provides
        SomeHomeDirService createService(GradleUserHomeDirProvider homeDirProvider) {
            return new SomeHomeDirService(homeDirProvider.gradleUserHomeDirectory)
        }
    }
}
