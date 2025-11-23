import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.jar.configureGradleJarTasks

plugins {
    id("gradlebuild.module-identity")
}

val gradleModule = the<GradleModuleExtension>()

configureGradleJarTasks()
