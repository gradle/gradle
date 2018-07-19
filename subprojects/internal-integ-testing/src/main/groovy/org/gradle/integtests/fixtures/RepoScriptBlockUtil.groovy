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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.GOOGLE
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.JCENTER
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.MAVEN_CENTRAL
import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.GOOGLE_URL
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import static org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.BINTRAY_JCENTER_URL

@CompileStatic
class RepoScriptBlockUtil {
    static enum MirroredRepository {
        JCENTER(BINTRAY_JCENTER_URL, System.getProperty('org.gradle.integtest.mirrors.jcenter'), "maven", "jcenter()"),
        MAVEN_CENTRAL(MAVEN_CENTRAL_URL, System.getProperty('org.gradle.integtest.mirrors.mavencentral'), "maven", "mavenCentral()"),
        GOOGLE(GOOGLE_URL, System.getProperty('org.gradle.integtest.mirrors.google'), "maven", "google()"),
        LIGHTBEND_MAVEN("https://repo.lightbend.com/lightbend/maven-releases", System.getProperty('org.gradle.integtest.mirrors.lightbendmaven'), "maven"),
        LIGHTBEND_IVY("https://repo.lightbend.com/lightbend/ivy-releases", System.getProperty('org.gradle.integtest.mirrors.lightbendivy'), "ivy"),
        SPRING_RELEASES('https://maven.springframework.org/release', System.getProperty('org.gradle.integtest.mirrors.springreleases'), 'maven'),
        SPRING_SNAPSHOTS('https://repo.spring.io/snapshot/', System.getProperty('org.gradle.integtest.mirrors.springsnapshots'), 'maven'),
        RESTLET('https://maven.restlet.com', System.getProperty('org.gradle.integtest.mirrors.restlet'), 'maven'),
        GRADLE('https://repo.gradle.org/gradle/repo', System.getProperty('org.gradle.integtest.mirrors.gradle'), 'maven'),
        JBOSS('https://repository.jboss.org/maven2/', System.getProperty('org.gradle.integtest.mirrors.jboss'), 'maven')

        String originalUrl
        String mirrorUrl
        String name
        String type
        String declaration

        private MirroredRepository(String originalUrl, String mirrorUrl, String type) {
            this(originalUrl, mirrorUrl, type, null)
        }

        private MirroredRepository(String originalUrl, String mirrorUrl, String type, String declaration) {
            this.originalUrl = originalUrl
            this.mirrorUrl = mirrorUrl ?: originalUrl
            this.name = mirrorUrl ? name() + "_MIRROR" : name()
            this.type = type
            this.declaration = declaration
        }

        String getRepositoryDefinition(GradleDsl dsl = GROOVY) {
            if (dsl == KOTLIN) {
                """
                    ${type} {
                        name = "${name}"
                        url = uri("${mirrorUrl}")
                    }
                """
            } else {
                """
                    ${type} {
                        name '${name}'
                        url '${mirrorUrl}'
                    }
                """
            }
        }

        void configure(RepositoryHandler repositories) {
            repositories.maven { MavenArtifactRepository repo ->
                repo.name = name
                repo.url = mirrorUrl
                System.out.println("name: ${repo.name}, url: ${repo.url}")
            }
        }
    }

    private RepoScriptBlockUtil() {
    }

    static String jcenterRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${jcenterRepositoryDefinition(dsl)}
            }
        """
    }

    static void configureJcenter(RepositoryHandler repositories) {
        MirroredRepository.JCENTER.configure(repositories)
    }

    static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${mavenCentralRepositoryDefinition(dsl)}
            }
        """
    }

    static String googleRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${googleRepositoryDefinition(dsl)}
            }
        """
    }

    static String mavenCentralRepositoryMirrorUrl() {
        def url = MirroredRepository.MAVEN_CENTRAL.mirrorUrl
        if (url.endsWith('/')) {
            url
        } else {
            url + '/'
        }
    }

    static String jcenterRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.JCENTER.getRepositoryDefinition(dsl)
    }

    static String mavenCentralRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.MAVEN_CENTRAL.getRepositoryDefinition(dsl)
    }

    static String lightbendMavenRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_MAVEN.getRepositoryDefinition(dsl)
    }

    static String lightbendIvyRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_IVY.getRepositoryDefinition(dsl)
    }

    static String googleRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GOOGLE.getRepositoryDefinition(dsl)
    }

    static String gradleRepositoryMirrorUrl() {
        MirroredRepository.GRADLE.mirrorUrl
    }

    static String gradleRepositoryDefintion(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE.getRepositoryDefinition(dsl)
    }

    static void replaceExternalRepos(File rootDir) {
        if (rootDir != null && rootDir.isDirectory()) {
            rootDir.eachFileRecurse { file ->
                if (file.name == 'build.gradle') {
                    replaceRepositoriesInBuildFile(file, GROOVY)
                } else if (file.name == 'build.gradle.kts') {
                    replaceRepositoriesInBuildFile(file, KOTLIN)
                }
            }
        }
    }

    static replaceRepositoriesInBuildFile(File file, GradleDsl dsl) {
        String text = file.text
        [JCENTER, MAVEN_CENTRAL, GOOGLE].each {
            text = text.replace(it.declaration, it.getRepositoryDefinition(dsl))
        }
        file.text = text
    }

    static File createMirrorInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        def mirrorConditions = MirroredRepository.values().collect { MirroredRepository mirror ->
            """
                if (normalizeUrl(repo.url) == normalizeUrl('${mirror.originalUrl}')) {
                    repo.url = '${mirror.mirrorUrl}'
                }
                
            """
        }.join("")
        mirrors << """
            import groovy.transform.CompileStatic
            import groovy.transform.CompileDynamic
            
            // Sometimes, applying this init script in a composite build will result in
            // java.lang.IllegalStateException: Root project has not been attached
            if(gradle.findIdentityPath()!=null) {
                gradle.pluginManager.apply(MirrorPlugin)
            }

            @CompileStatic
            class MirrorPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {
                    gradle.allprojects { Project project ->
                        project.buildscript.configurations["classpath"].incoming.beforeResolve {
                            withMirrors(project.buildscript.repositories)
                        }
                        project.afterEvaluate {
                            withMirrors(project.repositories)
                        }
                    }
                    maybeConfigurePluginManagement(gradle)
                }

                @CompileDynamic
                void maybeConfigurePluginManagement(Gradle gradle) {
                    if (gradle.gradleVersion >= "4.4") {
                        gradle.settingsEvaluated { Settings settings ->
                            withMirrors(settings.pluginManagement.repositories)
                        }
                    }
                }
                
                void withMirrors(RepositoryHandler repos) {
                    repos.all { repo ->
                        if (repo instanceof MavenArtifactRepository) {
                            mirror(repo)
                        } else if (repo instanceof IvyArtifactRepository) {
                            mirror(repo)
                        }
                    }
                }
    
                void mirror(MavenArtifactRepository repo) {
                    ${mirrorConditions}
                }

                void mirror(IvyArtifactRepository repo) {
                    ${mirrorConditions}
                }
                
                // We see them as equal:
                // https://repo.maven.apache.org/maven2/ and http://repo.maven.apache.org/maven2
                String normalizeUrl(Object url) {
                    String result = url.toString().replace('https://', 'http://')
                    return result.endsWith("/") ? result : result + "/"
                }
            }
        """
        mirrors
    }
}
