val login = tasks.register<Exec>("login") {
    val USERNAME_PROPERTY = "username"
    val PASSWORD_PROPERTY = "password"
    val username = providers.gradleProperty(USERNAME_PROPERTY).forUseAtConfigurationTime()
    val password = providers.gradleProperty(PASSWORD_PROPERTY).forUseAtConfigurationTime()

    doFirst {
        if (!username.isPresent() || !password.isPresent()) {
            throw GradleException("login task requires '$USERNAME_PROPERTY' and '$PASSWORD_PROPERTY' properties")
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
