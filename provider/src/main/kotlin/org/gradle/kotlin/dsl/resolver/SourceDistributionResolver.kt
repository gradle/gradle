package org.gradle.kotlin.dsl.resolver

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.create
import java.io.File

interface SourceDistributionResolver {
    fun downloadAndResolveSources(): Collection<File>
}

class DefaultSourceDistributionResolverImpl(val project: Project) : SourceDistributionResolver {
    companion object {
        val ARTIFACT_TYPE = Attribute.of("artifactType", String::class.java)
        val ZIP_TYPE = "zip"
        val SOURCES_DIRECTORY = "src-directory"
    }

    override
    fun downloadAndResolveSources(): Collection<File> {
        val repositories = project.repositories
        val repos = createSourceRepositories(repositories)
        registerTransforms()
        try {
            val sourceDependency = project.dependencies.create("gradle", "gradle", project.gradle.gradleVersion, null, "src", "zip")
            val configuration = project.configurations.detachedConfiguration(sourceDependency)
            configuration.attributes.attribute(ARTIFACT_TYPE, SOURCES_DIRECTORY)
            return configuration.files
        } finally {
            repos.forEach {
                repositories.remove(it)
            }
        }
    }

    private
    fun createSourceRepositories(repositories: RepositoryHandler): List<IvyArtifactRepository> {
        return listOf("distributions", "distributions-snapshots").map { repoName ->
            val repo = repositories.ivy {
                it.setName("Gradle ${repoName}")
                it.setUrl("https://services.gradle.org")
                it.layout("pattern") {
                    val layout = it as IvyPatternRepositoryLayout
                    layout.artifact("/${repoName}/[module]-[revision]-[classifier](.[ext])")
                }
            }
            // push the repository first in the list, for performance
            repositories.remove(repo)
            repositories.addFirst(repo)
            repo
        }
    }

    private
    fun registerTransforms() {
        project.dependencies.registerTransform {
            it.from.attribute(ARTIFACT_TYPE, ZIP_TYPE)
            it.to.attribute(ARTIFACT_TYPE, SOURCES_DIRECTORY)
            it.artifactTransform(ExtractGradleSourcesTransform::class.java)
        }
    }

}
