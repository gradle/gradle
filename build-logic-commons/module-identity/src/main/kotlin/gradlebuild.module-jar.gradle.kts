import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.jar.configureGradleModuleJarTasks

plugins {
    id("gradlebuild.module-identity")
}

val gradleModule = the<GradleModuleExtension>()

configureGradleModuleJarTasks()
