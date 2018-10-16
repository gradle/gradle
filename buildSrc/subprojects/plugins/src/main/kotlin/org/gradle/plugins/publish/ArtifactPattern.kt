package org.gradle.plugins.publish


fun createArtifactPattern(isSnapshot: Boolean, group: String, artifactName: String): String {
    assert(group.isNotEmpty())
    assert(artifactName.isNotEmpty())

    val libsType = if (isSnapshot) "snapshots" else "releases"
    val repoUrl = "https://gradle.artifactoryonline.com/gradle/libs-$libsType-local"
    val groupId = group.replace(".", "/")
    return "$repoUrl/$groupId/$artifactName/[revision]/[artifact]-[revision](-[classifier]).[ext]"
}
