plugins {
    id("application")
}

repositories {
    mavenCentral()
}

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

// tag::dependency-full-platform[]
abstract class KafkaAlignmentRule : ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        ctx.details.run {
            if (id.group == "org.apache.kafka") {
                belongsTo("org.apache.kafka:kafka-virtual-platform:${id.version}")
            }
        }
    }
}

dependencies {
    implementation("org.apache.kafka:kafka-streams:3.6.0")
    implementation("org.apache.kafka:kafka-clients:3.7.0")

    components.all<KafkaAlignmentRule>()
}
// end::dependency-full-platform[]
