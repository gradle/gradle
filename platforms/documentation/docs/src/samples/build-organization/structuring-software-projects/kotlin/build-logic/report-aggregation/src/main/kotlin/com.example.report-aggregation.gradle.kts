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
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }
}

// A resolvable configuration to collect source code
val sourcesPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.MAIN_SOURCES))
    }
}

// A resolvable configuration to collect JaCoCo coverage data
val coverageDataPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    extendsFrom(aggregate)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.JACOCO_RESULTS))
    }
}

// Register a code coverage report task to generate the aggregated report
val codeCoverageReport by tasks.registering(JacocoReport::class) {
    additionalClassDirs(classesPath.filter { it.isDirectory() })
    additionalSourceDirs(sourcesPath.incoming.artifactView { lenient(true) }.files)
    executionData(coverageDataPath.incoming.artifactView { lenient(true) }.files.filter { it.exists() })

    reports {
        html.required = true
        xml.required = true
    }
}

// Make JaCoCo report generation part of the 'check' lifecycle phase
tasks.check {
    dependsOn(codeCoverageReport)
}
