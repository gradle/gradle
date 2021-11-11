plugins {
    id("java-base") // we only apply 'java-base' as this plugin is for projects without source code
    id("jacoco")
}

// Configurations to declare dependencies
val aggregate by configurations.creating {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = false
}

// Resolvable configuration to resolve the classes of all dependencies
val classesPath by configurations.creating {
    isVisible = false
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.CLASSES))
    }
}

// A resolvable configuration to collect source code
val sourcesPath by configurations.creating {
    isVisible = false
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.VERIFICATION))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.SOURCES))
        attribute(Sources.SOURCES_ATTRIBUTE, objects.named(Sources::class.java, Sources.ALL_SOURCE_DIRS))
    }
}

// A resolvable configuration to collect JaCoCo coverage data
val coverageDataPath by configurations.creating {
    isVisible = false
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.VERIFICATION))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.JACOCO_COVERAGE))
    }
}

// Register a code coverage report task to generate the aggregated report
val codeCoverageReport by tasks.registering(JacocoReport::class) {
    classDirectories.from(classesPath.getIncoming().getFiles())
    sourceDirectories.from(sourcesPath.getIncoming().getFiles())
    executionData(coverageDataPath.getIncoming().getFiles().filter { it.exists() })

    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

// Make JaCoCo report generation part of the 'check' lifecycle phase
tasks.check {
    dependsOn(codeCoverageReport)
}
