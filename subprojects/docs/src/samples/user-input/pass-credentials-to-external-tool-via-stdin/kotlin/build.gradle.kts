val login = tasks.register<Exec>("login") {
    val USERNAME_PROPERTY = "username"
    val PASSWORD_PROPERTY = "password"
    val providers = project.getProviders()
    val username = providers.gradleProperty(USERNAME_PROPERTY)
    val password = providers.gradleProperty(PASSWORD_PROPERTY)

    doFirst {
        if (!username.isPresent() || !password.isPresent()) {
            throw GradleException(String.format("login task requires '%s' and '%s' properties",
                USERNAME_PROPERTY, PASSWORD_PROPERTY))
        }
    }

    standardInput = java.io.ByteArrayInputStream("${username.orNull}\n${password.orNull}".toByteArray())
    commandLine = listOf("sh", "login.sh")
}

tasks.register("doAuthenticated") {
    dependsOn(login)
    doLast {
        println("Doing authenticated task")
    }
}
