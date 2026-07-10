import gradlebuild.buildutils.tasks.UpdateInitPluginTemplateVersionFile

tasks {
    register<UpdateInitPluginTemplateVersionFile>("updateInitPluginTemplateVersionFile") {
        group = "Build init"
        libraryVersionFile = layout.projectDirectory.file(
            "src/main/resources/org/gradle/buildinit/tasks/templates/library-versions.properties"
        )
    }
}


