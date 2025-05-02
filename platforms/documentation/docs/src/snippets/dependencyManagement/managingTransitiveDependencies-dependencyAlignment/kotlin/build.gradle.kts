plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependencies[]
dependencies {
    // a dependency on Jackson Databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")

    // and a dependency on vert.x
    implementation("io.vertx:vertx-core:3.5.3")
}
// end::dependencies[]

if (project.hasProperty("useBom")) {
// tag::use_bom_rule[]
dependencies {
    components.all<JacksonBomAlignmentRule>()
}
// end::use_bom_rule[]
} else {
// tag::use_rule[]
dependencies {
    components.all<JacksonAlignmentRule>()
}
// end::use_rule[]
// tag::enforced_platform[]
dependencies {
    // Forcefully downgrade the virtual Jackson platform to 2.8.9
    implementation(enforcedPlatform("com.fasterxml.jackson:jackson-virtual-platform:2.8.9"))
}
// end::enforced_platform[]
}

// tag::bom-alignment-rule[]
abstract class JacksonBomAlignmentRule: ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        ctx.details.run {
            if (id.group.startsWith("com.fasterxml.jackson")) {
                // declare that Jackson modules belong to the platform defined by the Jackson BOM
                belongsTo("com.fasterxml.jackson:jackson-bom:${id.version}", false)
            }
        }
    }
}
// end::bom-alignment-rule[]

// tag::alignment-rule[]
abstract class JacksonAlignmentRule: ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        ctx.details.run {
            if (id.group.startsWith("com.fasterxml.jackson")) {
                // declare that Jackson modules all belong to the Jackson virtual platform
                belongsTo("com.fasterxml.jackson:jackson-virtual-platform:${id.version}")
            }
        }
    }
}
// end::alignment-rule[]
