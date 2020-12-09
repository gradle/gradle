/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import gradlebuild.classycle.tasks.Classycle
import gradlebuild.classycle.extension.ClassycleExtension

plugins {
    id("base")
    id("checkstyle")
    id("codenarc")
}

val codeQuality = tasks.register("codeQuality") {
    dependsOn(tasks.matching { it is CodeNarc || it is Checkstyle || it is Classycle || it is ValidatePlugins })
}
tasks.withType<Test>().configureEach {
    shouldRunAfter(codeQuality)
}
tasks.check {
    dependsOn(codeQuality)
}

val rules by configurations.creating {
    isVisible = false
    isCanBeConsumed = false

    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.RESOURCES))
    }
}

val classycle by configurations.creating {
    isVisible = false
    isCanBeConsumed = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

dependencies {
    rules("gradlebuild:code-quality-rules") {
        because("Provides rules defined in XML files")
    }
    codenarc("gradlebuild:code-quality-rules") {
        because("Provides the IntegrationTestFixturesRule implementation")
    }
    codenarc("org.codenarc:CodeNarc:1.5")
    codenarc(embeddedKotlin("stdlib"))

    classycle("classycle:classycle:1.4.2@jar")

    components {
        withModule<CodeNarcRule>("org.codenarc:CodeNarc")
    }
}

fun configFile(fileName: String) = resources.text.fromFile(rules.asFileTree.filter { it.name == fileName })

checkstyle {
    toolVersion = "8.12"
    config = configFile("checkstyle.xml")
    configDirectory.set(rules.elements.map { layout.projectDirectory.dir(it.single().asFile.absolutePath).dir("checkstyle") })
}

plugins.withType<GroovyBasePlugin> {
    the<SourceSetContainer>().all {
        tasks.register<Checkstyle>(getTaskName("checkstyle", "groovy")) {
            config = configFile("checkstyle-groovy.xml")
            source(allGroovy)
            classpath = compileClasspath
            reports.xml.destination = checkstyle.reportsDir.resolve("${this@all.name}-groovy.xml")
        }
    }
}

codenarc {
    config = configFile("codenarc.xml")
}

tasks.withType<CodeNarc>().configureEach {
    reports.xml.isEnabled = true
    if (name.contains("IntegTest")) {
        config = configFile("codenarc-integtests.xml")
    }
}

val classycleExtension = extensions.create<ClassycleExtension>("classycle").apply {
    excludePatterns.convention(emptyList())
}

extensions.findByType<SourceSetContainer>()?.all {
    tasks.register<Classycle>(getTaskName("classycle", null)) {
        classesDirs.from(output.classesDirs)
        excludePatterns.set(classycleExtension.excludePatterns)
        reportName.set(this@all.name)
        reportDir.set(reporting.baseDirectory.dir("classycle"))
        reportResourcesZip.from(rules)
    }
}

// Empty source dirs produce cache misses, and are not caught by `git status`.
// Fail if we find any.
tasks.withType<SourceTask>().configureEach {
    doFirst {
        source.visit {
            if (file.isDirectory && file.listFiles()?.isEmpty() == true) {
                throw IllegalStateException("Empty src dir found. This causes build cache misses. See github.com/gradle/gradle/issues/2463.\nRun the following command to fix it.\nrmdir ${file.absolutePath}")
            }
        }
    }
}

val SourceSet.allGroovy: SourceDirectorySet
    get() = withConvention(GroovySourceSet::class) { allGroovy }

abstract class CodeNarcRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.group == "org.codehaus.groovy" }
                add("org.gradle.groovy:groovy-all") {
                    version { prefer("1.3-" + groovy.lang.GroovySystem.getVersion()) }
                    because("We use groovy-all everywhere")
                }
            }
        }
    }
}

