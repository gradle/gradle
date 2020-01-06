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

// This script can not be moved to buildSrc/main/kotlin as it is also used by the buildSrc build.

// As this script is also accessed from the buildSrc project,
// we can't use rootProject for the path as both builds share the same config directory.
// Work around https://github.com/gradle/kotlin-dsl/issues/736, remove this once fixed
val effectiveRootDir: File =
    if (rootDir.name == "buildSrc") rootDir.parentFile
    else rootDir

val codeQualityConfigDir = effectiveRootDir.resolve("config")

configureCheckstyle(codeQualityConfigDir)
configureCodenarc(codeQualityConfigDir)
configureCodeQualityTasks()

fun Project.configureCheckstyle(codeQualityConfigDir: File) {
    apply(plugin = "checkstyle")

    val checkStyleConfigDir = codeQualityConfigDir.resolve("checkstyle")
    configure<CheckstyleExtension> {
        configDirectory.set(checkStyleConfigDir)
        toolVersion = "8.12"

        plugins.withType<GroovyBasePlugin> {
            java.sourceSets.all {
                tasks.register(getTaskName("checkstyle", "groovy"), Checkstyle::class.java) {
                    configFile = checkStyleConfigDir.resolve("checkstyle-groovy.xml")
                    source(allGroovy)
                    classpath = compileClasspath
                    reports.xml.destination = reportsDir.resolve("${this@all.name}-groovy.xml")
                }
            }
        }
    }
}

fun getIntegrationTestFixturesRule(): Class<*>? {
    try {
        return Class.forName("org.gradle.gradlebuild.buildquality.codenarc.IntegrationTestFixturesRule", false, this.javaClass.classLoader)
    } catch (e: Throwable) {
        return null
    }
}

fun Project.configureCodenarc(codeQualityConfigDir: File) {
    apply(plugin = "codenarc")

    dependencies {
        "codenarc"("org.codenarc:CodeNarc:1.0")
        components {
            withModule("org.codenarc:CodeNarc", CodeNarcRule::class.java)
        }
        val ruleClass = getIntegrationTestFixturesRule()
        if (ruleClass != null) {
            "codenarc"(files(ruleClass.protectionDomain!!.codeSource!!.location))
            "codenarc"(embeddedKotlin("stdlib"))
        }
    }

    configure<CodeNarcExtension> {
        configFile = codeQualityConfigDir.resolve("codenarc.xml")
    }

    tasks.withType(CodeNarc::class.java).configureEach {
        reports.xml.isEnabled = true
        if (name.contains("IntegTest")) {
            configFile = codeQualityConfigDir.resolve("codenarc-integtests.xml")
        }
    }
}


fun Project.configureCodeQualityTasks() {
    val codeQualityTasks = tasks.matching { it is CodeNarc || it is Checkstyle }
    tasks.register("codeQuality").configure {
        dependsOn(codeQualityTasks)
    }
    tasks.withType(Test::class.java).configureEach {
        shouldRunAfter(codeQualityTasks)
    }
}


val Project.java
    get() = the<JavaPluginConvention>()

val SourceSet.allGroovy: SourceDirectorySet
    get() = withConvention(GroovySourceSet::class) { allGroovy }

open class CodeNarcRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.group == "org.codehaus.groovy" }
                add("org.gradle.groovy:groovy-all") {
                    // TODO This must match the version number in dependencies.gradle
                    version { prefer("1.3-" + groovy.lang.GroovySystem.getVersion()) }
                    because("We use groovy-all everywhere")
                }
            }
        }
    }
}
