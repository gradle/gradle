/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.ivy.IvyModule

class RepositoryInteractionDependencyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private static final REPO_TYPES = ['maven', 'ivy', 'maven-gradle', 'ivy-gradle']
    private static final TEST_VARIANTS = [
        'default':          '',
        'runtime':          'configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))',
        'api':              'configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))',
    ]

    def setup() {
        // apply Java ecosystem rules
        buildFile << """
            plugins {
                id("jvm-ecosystem")
            }
        """
    }

    private static boolean leaksRuntime(testVariant, repoType, prevRepoType = null) {
        if (testVariant == 'runtime' || testVariant == 'default') {
            // the runtime variant is supposed to include everything
            return true
        }
        if (testVariant == 'api' && repoType == 'ivy') {
            // classic ivy metadata interpretation does not honor api/runtime separation
            return true
        }
        return false
    }

    private static String expectedConfiguration(repoType, testVariant) {
        if (repoType.contains('gradle')) {
            if (testVariant.contains('api')) {
                return 'api'
            }
            return 'runtime'
        }
        if (repoType == 'maven') {
            switch (testVariant) {
                case 'api':
                    return 'compile'
                default:
                    return 'runtime'
            }
        }
        return 'default'
    }

    private ResolveTestFixture resolve
    private Map<String, RemoteRepositorySpec> repoSpecs = [:]
    private Map<String, HttpRepository> repos = [:]

    private String configuredRepository(String repoType) {
        def isMaven = repoType.contains('maven')
        def gradleMetadata = repoType.contains('gradle')
        HttpRepository repo = isMaven ?
            mavenHttpRepo(repoType, gradleMetadata ? HttpRepository.MetadataType.ONLY_GRADLE : HttpRepository.MetadataType.ONLY_ORIGINAL)
            : ivyHttpRepo(repoType, gradleMetadata ? HttpRepository.MetadataType.ONLY_GRADLE : HttpRepository.MetadataType.ONLY_ORIGINAL)

        repos += [(repoType): repo]
        """
        repositories {
            ${isMaven ? 'maven' : 'ivy'} {
                url = "${repo.uri}"
                metadataSources { ${gradleMetadata ? 'gradleMetadata()' : isMaven ? 'mavenPom()' : 'ivyDescriptor()'} }
            }
        }
        """
    }

    def setupRepositories(repoTypes) {
        repoTypes.each { repoType ->
            repoSpecs += [(repoType): new RemoteRepositorySpec()]

            buildFile << """
                ${configuredRepository(repoType)}
            """
        }

        def conf = "conf"
        resolve = new ResolveTestFixture(buildFile, conf)
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        buildFile << """
            configurations {
                $conf
            }
        """
        resolve.addDefaultVariantDerivationStrategy()

        repoTypes.each { repoType ->
            repository(repoType) {
                "org:$repoType-api-dependency:1.0"()
                "org:$repoType-runtime-dependency:1.0"()
                "org:$repoType:1.0" {
                    variant("api") {
                        dependsOn "org:$repoType-api-dependency:1.0"
                    }
                    variant("runtime") {
                        dependsOn "org:$repoType-api-dependency:1.0"
                        dependsOn "org:$repoType-runtime-dependency:1.0"
                    }
                }
            }
        }
    }

    def setupModuleChain(chain) {
        String prevRepoType = null
        chain.each { repoType ->
            if (prevRepoType) {
                repository(prevRepoType) {
                    "org:$prevRepoType:1.0" {
                        variant("api") {
                            dependsOn "org:$repoType:1.0"
                        }
                        variant("runtime") {
                            dependsOn "org:$repoType:1.0"
                        }
                    }
                }
            }
            prevRepoType = repoType
        }
    }

    def expectChainInteractions(repoTypes, chain, testVariant = 'api', configuration = null) {
        String prevRepoType = null
        chain.each { repoType ->
            repositoryInteractions(repoType) {
                "org:$repoType:1.0" {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                "org:$repoType-api-dependency:1.0" {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                if (leaksRuntime(testVariant, repoType, prevRepoType) || (prevRepoType==null && configuration in ['test', 'runtime'])) {
                    "org:$repoType-runtime-dependency:1.0" {
                        expectGetMetadata()
                        expectGetArtifact()
                    }
                }
                repoTypes.subList(repoTypes.indexOf(repoType) + 1, repoTypes.size()).each { other ->
                    "org:$other:1.0" {
                        expectGetMetadataMissingThatIsFoundElsewhere()
                    }
                    "org:$other-api-dependency:1.0" {
                        expectGetMetadataMissingThatIsFoundElsewhere()
                    }
                    if (leaksRuntime(testVariant, other, chain.indexOf(other) > 0? chain[chain.indexOf(other) - 1] : null)) {
                        "org:$other-runtime-dependency:1.0" {
                            expectGetMetadataMissingThatIsFoundElsewhere()
                        }
                    }
                }
            }
            prevRepoType = repoType
        }
    }

    void repository(String repoType, @DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        spec.delegate = repoSpecs[repoType]
        spec()
    }

    void repositoryInteractions(String repoType, @DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        RemoteRepositorySpec.DEFINES_INTERACTIONS.set(true)
        try {
            spec.delegate = repoSpecs[repoType]
            spec()
            repoSpecs[repoType].build(repos[repoType])
        } finally {
            RemoteRepositorySpec.DEFINES_INTERACTIONS.set(false)
        }
    }

    def "selects #testVariant variant of each dependency in every repo supporting it #chain"() {
        given:
        setupRepositories(REPO_TYPES)
        setupModuleChain(chain)

        when:
        buildFile << """
            ${TEST_VARIANTS[testVariant]}
            dependencies {
                conf 'org:${chain[0]}:1.0'
            }
        """
        expectChainInteractions(REPO_TYPES, chain, testVariant)

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:${chain[0]}:1.0:${RepositoryInteractionDependencyResolveIntegrationTest.expectedConfiguration(chain[0], testVariant)}") {
                    module "org:${chain[0]}-api-dependency:1.0"
                    if (RepositoryInteractionDependencyResolveIntegrationTest.leaksRuntime(testVariant, chain[0])) { module "org:${chain[0]}-runtime-dependency:1.0" }
                    module("org:${chain[1]}:1.0") {
                        module "org:${chain[1]}-api-dependency:1.0"
                        if (RepositoryInteractionDependencyResolveIntegrationTest.leaksRuntime(testVariant, chain[1], chain[0])) { module "org:${chain[1]}-runtime-dependency:1.0" }
                        module("org:${chain[2]}:1.0") {
                            module "org:${chain[2]}-api-dependency:1.0"
                            if (RepositoryInteractionDependencyResolveIntegrationTest.leaksRuntime(testVariant, chain[2], chain[1])) { module "org:${chain[2]}-runtime-dependency:1.0" }
                            module("org:${chain[3]}:1.0") {
                                module "org:${chain[3]}-api-dependency:1.0"
                                if (RepositoryInteractionDependencyResolveIntegrationTest.leaksRuntime(testVariant, chain[3], chain[2])) { module "org:${chain[3]}-runtime-dependency:1.0" }
                            }
                        }
                    }
                }
            }
        }

        where:
        [chain, testVariant] << [REPO_TYPES.permutations(), TEST_VARIANTS.keySet()].combinations()
    }

    def "resolution works for a chain of pure maven dependencies"() {
        given:
        def modules = ['mavenCompile1', 'mavenCompile2', 'mavenCompile3']
        setupRepositories(modules)
        setupModuleChain(modules)

        when:
        buildFile << """
            dependencies {
                configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                conf group: 'org', name: 'mavenCompile1', version: '1.0'
            }
        """
        expectChainInteractions(modules, modules)

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:mavenCompile1:1.0:compile") {
                    module "org:mavenCompile1-api-dependency:1.0"
                    module("org:mavenCompile2:1.0") {
                        module "org:mavenCompile2-api-dependency:1.0"
                        module("org:mavenCompile3:1.0") {
                            module "org:mavenCompile3-api-dependency:1.0"
                        }
                    }
                }
            }
        }
    }

    def "resolution with Gradle metadata propagates to Maven dependencies"() {
        given:
        def modules = ['mavenCompile1', 'mavenCompile2', 'maven-gradle', 'maven']
        setupRepositories(modules)
        setupModuleChain(modules)

        when:
        buildFile << """
            dependencies {
                configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                conf group: 'org', name: 'mavenCompile1', version: '1.0'
            }
        """
        expectChainInteractions(modules, modules)

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:mavenCompile1:1.0:compile") {
                    module "org:mavenCompile1-api-dependency:1.0"
                    module("org:mavenCompile2:1.0") {
                        module "org:mavenCompile2-api-dependency:1.0"
                        module("org:maven-gradle:1.0") {
                            module "org:maven-gradle-api-dependency:1.0"
                            module("org:maven:1.0") {
                                module "org:maven-api-dependency:1.0"
                            }
                        }
                    }
                }
            }
        }
    }

    def "resolution works for maven dependencies"() {
        given:
        def modules = ['maven', 'mavenCompile1', 'maven-gradle', 'mavenCompile2']
        setupRepositories(modules)
        setupModuleChain(modules)

        when:
        buildFile << """
            dependencies {
                configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                conf group: 'org', name: 'maven', version: '1.0'
            }
        """
        expectChainInteractions(modules, modules, 'api')

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:maven:1.0") {
                    module "org:maven-api-dependency:1.0"
                    module("org:mavenCompile1:1.0") {
                        module "org:mavenCompile1-api-dependency:1.0"
                        module("org:maven-gradle:1.0") {
                            module "org:maven-gradle-api-dependency:1.0"
                            module("org:mavenCompile2:1.0") {
                                module "org:mavenCompile2-api-dependency:1.0"
                            }
                        }
                    }
                }
            }
        }
    }

    def "explicit configuration selection in ivy modules is NOT supported if targeting a #target module"() {
        given:
        setupRepositories([target, 'ivy'])
        repository('ivy') {
            "org:ivy:1.0" {
                withModule(IvyModule) {
                    dependsOn([organisation: 'org', module: target, revision: '1.0', conf: 'runtime->compile'])
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf 'org:ivy:1.0'
            }
        """
        expectChainInteractions([target, 'ivy'], ['ivy', target], 'runtime')

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:ivy:1.0") {
                    module "org:ivy-api-dependency:1.0"
                    module "org:ivy-runtime-dependency:1.0"
                    module("org:$target:1.0") {
                        module "org:$target-api-dependency:1.0"
                        module "org:$target-runtime-dependency:1.0"
                    }
                }
            }
        }

        where:
        target << ['maven', 'maven-gradle', 'ivy-gradle']
    }

    def "explicit configuration selection in ivy modules is supported if targeting a ivy module"() {
        given:
        // use a different name if selection is supported to not follow the default expectations defined in leaksRuntime()
        String targetRepoName = "ivy-select"
        setupRepositories([targetRepoName, 'ivy'])
        repository('ivy') {
            "org:ivy:1.0" {
                withModule(IvyModule) {
                    dependsOn([organisation: 'org', module: targetRepoName, revision: '1.0', conf: 'runtime->compile'])
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf 'org:ivy:1.0'
            }
        """
        expectChainInteractions([targetRepoName, 'ivy'], ['ivy', targetRepoName], 'api')

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org:ivy:1.0") {
                    module "org:ivy-api-dependency:1.0"
                    module "org:ivy-runtime-dependency:1.0"
                    module("org:$targetRepoName:1.0") {
                        module "org:$targetRepoName-api-dependency:1.0"
                    }
                }
            }
        }
    }
}
