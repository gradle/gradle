plugins {
    id("application")
}

repositories {
    mavenCentral()
}
/*
// tag::dependency-full[]
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")
    implementation("io.vertx:vertx-core:3.5.3")
}
// end::dependency-full[]

// tag::dependency-full-bom[]
dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.8.9"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")
    implementation("io.vertx:vertx-core:3.5.3")
}
// end::dependency-full-bom[]
*/
// tag::dependency-full-platform[]
abstract class JacksonAlignmentRule : ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        ctx.details.run {
            if (id.group.startsWith("com.fasterxml.jackson")) {
                belongsTo("com.fasterxml.jackson:jackson-virtual-platform:${id.version}")
            }
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")
    implementation("io.vertx:vertx-core:3.5.3")

    dependencies {
        components.all<JacksonAlignmentRule>()
    }
}
// end::dependency-full-platform[]

