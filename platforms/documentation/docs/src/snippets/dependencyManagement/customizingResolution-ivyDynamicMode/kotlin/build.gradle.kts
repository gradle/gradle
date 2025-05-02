// tag::ivy-repo-dynamic-mode[]
// Can enable dynamic resolve mode when you define the repository
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
        resolve.isDynamicMode = true
    }
}

// Can use a rule instead to enable (or disable) dynamic resolve mode for all repositories
repositories.withType<IvyArtifactRepository> {
    resolve.isDynamicMode = true
}
// end::ivy-repo-dynamic-mode[]
