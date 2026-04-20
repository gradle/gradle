/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gradle.api.tasks.diagnostics.internal.repositories.model

import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.internal.artifacts.repositories.RepositoryContentDescriptorInternal
import org.gradle.api.provider.Property
import org.gradle.authentication.Authentication
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import spock.lang.Specification

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.SETTINGS

class RepositoryReportModelFactoryTest extends Specification {
    def factory = new RepositoryReportModelFactory()

    def "converts MavenArtifactRepository"() {
        given:
        def repo = Stub(TestMavenRepo) {
            getName() >> "MavenRepo"
            getUrl() >> URI.create("https://repo.maven.apache.org/maven2/")
            isAllowInsecureProtocol() >> false
            getAuthentication() >> Stub(AuthenticationContainer)
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> []
                describeExcludeRules() >> []
                getIncludedConfigurations() >> null
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        def site = new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories")

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PLUGINS] as Set, site)

        then:
        result.name == "MavenRepo"
        result.type == RepositoryType.MAVEN
        result.location == "https://repo.maven.apache.org/maven2/"
        result.secure
        result.authSchemes == []
        !result.hasCredentials()
        result.contentFilter.isEmpty()
        result.roles == [RepositoryRole.PLUGINS] as Set
        result.declarationSite == site
    }

    def "converts FlatDirectoryArtifactRepository to dirs location"() {
        given:
        def descriptor = Stub(RepositoryContentDescriptorInternal) {
            describeIncludeRules() >> []
            describeExcludeRules() >> []
            getIncludedConfigurations() >> null
            getExcludedConfigurations() >> null
            getRequiredAttributes() >> null
        }
        def repo = Stub(TestFlatDirRepo) {
            getName() >> "flat"
            getDirs() >> ([new File("/tmp/a"), new File("/tmp/b")] as Set)
            getRepositoryDescriptorCopy() >> descriptor
        }
        def site = new RepositoryDeclarationSite(SETTINGS, null, "repositories")

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)

        then:
        result.type == RepositoryType.FLAT_DIR
        result.location.startsWith("dirs:[")
        result.location.contains("/tmp/a")
        result.location.contains("/tmp/b")
        result.secure // FlatDir always reported as secure
    }

    def "marks repo as insecure when allowInsecureProtocol=true"() {
        given:
        def repo = Stub(TestMavenRepo) {
            getName() >> "corp"
            getUrl() >> URI.create("http://corp.example.com/maven/")
            isAllowInsecureProtocol() >> true
            getAuthentication() >> Stub(AuthenticationContainer)
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> []
                describeExcludeRules() >> []
                getIncludedConfigurations() >> null
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PLUGINS] as Set,
            new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories"))

        then:
        !result.secure
    }

    def "captures content filter rules"() {
        given:
        def repo = Stub(TestMavenRepo) {
            getName() >> "filtered"
            getUrl() >> URI.create("https://example.com/")
            isAllowInsecureProtocol() >> false
            getAuthentication() >> Stub(AuthenticationContainer)
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> ['includeGroup("com.example")']
                describeExcludeRules() >> ['excludeModule("a", "b")']
                getIncludedConfigurations() >> (["compile"] as Set)
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(SETTINGS, null, "dependencyResolutionManagement.repositories"))

        then:
        result.contentFilter.includeRules == ['includeGroup("com.example")']
        result.contentFilter.excludeRules == ['excludeModule("a", "b")']
        result.contentFilter.onlyForConfigurations == ["compile"] as Set
    }

    def "extracts authentication scheme class names sorted alphabetically"() {
        given:
        // Use concrete implementations named exactly 'BasicAuthentication'/'DigestAuthentication'
        // so getClass().getSimpleName() returns those strings. Interface mocks/proxies would yield
        // generated names like '$Proxy12'.
        def basic = new BasicAuthentication()
        def digest = new DigestAuthentication()
        def auths = [basic, digest] as List<Authentication>
        def authContainer = Stub(AuthenticationContainer) {
            isEmpty() >> false
            size() >> auths.size()
            iterator() >> { auths.iterator() }
        }
        def repo = Stub(TestMavenRepo) {
            getName() >> "secured"
            getUrl() >> URI.create("https://secure.example.com/")
            isAllowInsecureProtocol() >> false
            getAuthentication() >> authContainer
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> []
                describeExcludeRules() >> []
                getIncludedConfigurations() >> null
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(SETTINGS, null, "repositories"))

        then:
        result.authSchemes == ['BasicAuthentication', 'DigestAuthentication']
    }

    def "detects credentials when configured credentials Property is present"() {
        given:
        def credentialsProperty = Stub(Property) {
            isPresent() >> true
        }
        def repo = Stub(TestAuthMavenRepo) {
            getName() >> "secured"
            getUrl() >> URI.create("https://secure.example.com/")
            isAllowInsecureProtocol() >> false
            getAuthentication() >> Stub(AuthenticationContainer)
            getConfiguredCredentials() >> credentialsProperty
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> []
                describeExcludeRules() >> []
                getIncludedConfigurations() >> null
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(SETTINGS, null, "repositories"))

        then:
        result.hasCredentials()
    }

    def "reports no credentials when configured credentials Property is empty"() {
        given:
        def credentialsProperty = Stub(Property) {
            isPresent() >> false
        }
        def repo = Stub(TestAuthMavenRepo) {
            getName() >> "open"
            getUrl() >> URI.create("https://open.example.com/")
            isAllowInsecureProtocol() >> false
            getAuthentication() >> Stub(AuthenticationContainer)
            getConfiguredCredentials() >> credentialsProperty
            getRepositoryDescriptorCopy() >> Stub(RepositoryContentDescriptorInternal) {
                describeIncludeRules() >> []
                describeExcludeRules() >> []
                getIncludedConfigurations() >> null
                getExcludedConfigurations() >> null
                getRequiredAttributes() >> null
            }
        }

        when:
        def result = factory.toReportRepository(repo, [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(SETTINGS, null, "repositories"))

        then:
        !result.hasCredentials()
    }

    // These abstract classes merge AbstractArtifactRepository with the per-type API so Spock can stub both.
    // (AbstractArtifactRepository is an abstract class, not an interface, so we use 'extends' + 'implements'.)
    static abstract class TestMavenRepo extends AbstractArtifactRepository implements MavenArtifactRepository {
        TestMavenRepo() { super(null, null) }
    }
    static abstract class TestFlatDirRepo extends AbstractArtifactRepository implements FlatDirectoryArtifactRepository {
        TestFlatDirRepo() { super(null, null) }
    }
    // Adds AuthenticationSupportedInternal so the factory's instanceof check for credential
    // detection via getConfiguredCredentials() applies to this stub.
    static abstract class TestAuthMavenRepo extends AbstractArtifactRepository implements MavenArtifactRepository, AuthenticationSupportedInternal {
        TestAuthMavenRepo() { super(null, null) }
    }

    // Concrete classes named identically to the SPI interfaces so the factory's
    // `getClass().getSimpleName()` lookup yields 'BasicAuthentication' / 'DigestAuthentication'.
    static class BasicAuthentication implements org.gradle.authentication.http.BasicAuthentication {
        @Override String getName() { "basic" }
    }
    static class DigestAuthentication implements org.gradle.authentication.http.DigestAuthentication {
        @Override String getName() { "digest" }
    }
}
