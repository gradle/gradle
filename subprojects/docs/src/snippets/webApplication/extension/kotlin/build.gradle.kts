plugins {
    `war`
}

// tag::war-extension[]
warPlugin {
    webAppDir.set(file("src/main/my-webapp"))
}
// end::war-extension[]
