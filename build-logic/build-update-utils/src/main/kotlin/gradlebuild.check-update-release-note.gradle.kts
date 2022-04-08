import gradlebuild.buildutils.tasks.CheckContributorsInReleaseNote
import gradlebuild.buildutils.tasks.UpdateContributorsInReleaseNote

tasks.register<CheckContributorsInReleaseNote>("checkContributorsInReleaseNote") {
    releaseNote.set(layout.projectDirectory.file("subprojects/docs/src/docs/release/notes.md"))
}
tasks.register<UpdateContributorsInReleaseNote>("updateContributorsInReleaseNote") {
    releaseNote.set(layout.projectDirectory.file("subprojects/docs/src/docs/release/notes.md"))
}
