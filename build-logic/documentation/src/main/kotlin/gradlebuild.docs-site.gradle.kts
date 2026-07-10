// Convention for projects that assemble the published Gradle documentation site.
//
// Declares the Gradle wiring needed to:
// - consume the rendered reference documentation (javadoc, kotlin-dsl, dsl) produced
//   by `:docs` via the `referenceDocs` resolvable configuration
// - expose the assembled site as the `gradleDocumentationSiteElements` consumable
//   so distribution packaging can pick it up
//
// Project-specific bits (task registrations, artifact wiring, where the site lands on
// disk) stay in the applying project's build script.

val referenceDocsDeps = configurations.dependencyScope("referenceDocs").get()
configurations.resolvable("referenceDocsClasspath") {
    extendsFrom(referenceDocsDeps)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-reference-documentation"))
    }
}

dependencies {
    add(referenceDocsDeps.name, project(":docs"))
}

configurations.consumable("gradleDocumentationSiteElements") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation-site"))
    }
}
