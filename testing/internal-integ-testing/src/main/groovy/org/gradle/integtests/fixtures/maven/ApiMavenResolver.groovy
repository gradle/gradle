/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures.maven

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.LocalRepositoryManager
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResult
import org.eclipse.aether.supplier.RepositorySystemSupplier
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transfer.TransferResource
import org.gradle.integtests.fixtures.RepoScriptBlockUtil

/**
 * Performs resolution of dependencies against Maven repositories.
 *
 * <p>This resolver leverages Maven's stable public API for dependency resolution and does
 * not run a full Maven build.</p>
 */
class ApiMavenResolver {

    /**
     * The local repository used for caching resolved artifacts.
     * This is the the repository usually located at ~/.m2/repository.
     */
    private final File localRepoDir

    /**
     * The URI of the repository to resolve artifacts from.
     * This repository should contain the artifacts under test.
     */
    private final URI testRepoUri

    ApiMavenResolver(File localRepoDir, URI testRepoUri) {
        this.localRepoDir = localRepoDir
        this.testRepoUri = testRepoUri
    }

    /**
     * Resolves the provided dependency, returning the dependency graph and resolved artifacts.
     */
    MavenResolutionResult resolveDependency(String group, String module, String version) {
        long time = System.currentTimeMillis()

        println()
        println("Resolving Maven artifact ${group}:${module}:${version}")
        MavenResolutionResult output = doResolveDependency(group, module, version)
        println("Resolved Maven graph in ${System.currentTimeMillis() - time} ms")

        return output
    }

    private MavenResolutionResult doResolveDependency(String group, String module, String version) {
        // Configures the majority of the infrastructure required for resolving dependencies.
        // This is the dependency-injection-less approach to obtain a `RepositorySystem`.
        // Maven generally recommends using Eclipse Sisu and Guice for DI, however this approach is also supported.
        RepositorySystem repoSystem = new RepositorySystemSupplier().get()

        // `MavenRepositorySystemUtils` is _technically_ internal, however it is also widely used across
        // many software projects embedding Maven and is unlikely to change.
        // If this ever does change, we can easily copy the code directly here.
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            void transferSucceeded(TransferEvent event) {
                TransferResource resource = event.getResource()
                long contentLength = event.getTransferredBytes()

                long duration = System.currentTimeMillis() - resource.getTransferStartTime()
                println("Downloaded ${contentLength} bytes in ${duration} ms from ${resource.getRepositoryId()}: ${resource.getResourceName()}")
            }
        })

        // Setup local cache repository
        // We cannot reuse this repository between tests since it will contain artifacts from the test repo.
        // There does not seem to be a way to get Maven to avoid caching artifacts from the test repo.
        LocalRepository localRepo = new LocalRepository(localRepoDir)
        LocalRepositoryManager lrm = repoSystem.newLocalRepositoryManager(session, localRepo)
        session.setLocalRepositoryManager(lrm)

        def graphResolutionRequest = new CollectRequest()
            .setRepositories([
                new RemoteRepository.Builder("test-repo", "default", testRepoUri.toString()).build(),
                new RemoteRepository.Builder("central", "default", RepoScriptBlockUtil.mavenCentralMirrorUrl).build()
            ])
            .addDependency(new Dependency(
                new DefaultArtifact(group, module, null, "jar", version),
                "runtime"
            ))

        // Resolve graph & rethrow any exceptions
        CollectResult graph = repoSystem.collectDependencies(session, graphResolutionRequest)
        graph.getExceptions().each { throw it }

        // Resolve artifacts to ensure they are all present
        DependencyResult result = repoSystem.resolveDependencies(session, new DependencyRequest(graph.getRoot(), null))

        // Rethrow artifact exceptions if necessary
        // Maven returns an exception if an artifact is found in one repo but not another, so we need extra logic here
        Set<String> allRepoIds = graph.request.repositories.collect { it.id } as Set
        result.artifactResults.each {
            assert it.artifact.file

            Set<String> missingRepos = it.getExceptions()
                .findAll { it instanceof ArtifactNotFoundException }
                .collect { ((ArtifactNotFoundException) it).repository.id } as Set
            assert missingRepos.size() < allRepoIds.size(): "Artifact ${it.getArtifact()} not found in any repository."

            it.getExceptions().findAll { !(it instanceof ArtifactNotFoundException) }.each { throw it }
        }

        new MavenResolutionResult(graph.getRoot(), result.artifactResults)
    }

    static class MavenResolutionResult {

        final List<Artifact> artifacts
        final DependencyNode root

        private MavenResolutionResult(DependencyNode root, List<ArtifactResult> artifacts) {
            this.root = root
            this.artifacts = artifacts*.artifact
        }

        /**
         * Returns a list of all artifacts that are direct transitive dependencies of the resolved dependency.
         *
         * <p>Only a subset of the graph is returned.</p>
         */
        List<String> getFirstLevelDependencies() {
            // The root has a single child, where the first child is our requested dependency's node
            def requestedNode = root.children.first()

            def firstLevelNodes = requestedNode.children
            return firstLevelNodes*.artifact*.toString()
        }

        /**
         * Returns a set of all file names resolved for the dependency, including transitive dependencies.
         */
        Set<String> getArtifactFileNames() {
            artifacts*.file*.name as Set
        }
    }
}
