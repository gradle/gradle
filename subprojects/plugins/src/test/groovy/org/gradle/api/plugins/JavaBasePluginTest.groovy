/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.model.DefaultMultipleCandidateResult
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.api.file.FileCollectionMatchers.sameCollection
import static org.gradle.api.reflect.TypeOf.typeOf
import static org.gradle.util.internal.WrapUtil.toLinkedSet

class JavaBasePluginTest extends AbstractProjectBuilderSpec {
    @Rule
    public SetSystemProperties sysProperties = new SetSystemProperties()

    def "applies base plugins and adds convention and extensions"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        project.plugins.hasPlugin(ReportingBasePlugin)
        project.plugins.hasPlugin(BasePlugin)
        project.plugins.hasPlugin(JvmEcosystemPlugin)
        project.convention.plugins.java instanceof JavaPluginConvention
        project.extensions.sourceSets.is(project.convention.plugins.java.sourceSets)
        project.extensions.java instanceof JavaPluginExtension
    }

    def "sourceSets extension is exposed as SourceSetContainer"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        project.extensions.extensionsSchema.find { it.name == "sourceSets" }.publicType == typeOf(SourceSetContainer)
    }

    def "properties on convention and extension are synchronized"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def ext = project.extensions.java
        project.sourceCompatibility == JavaVersion.current()
        project.targetCompatibility == JavaVersion.current()
        ext.sourceCompatibility == JavaVersion.current()
        ext.targetCompatibility == JavaVersion.current()

        when:
        project.sourceCompatibility = JavaVersion.VERSION_1_6

        then:
        project.sourceCompatibility == JavaVersion.VERSION_1_6
        project.targetCompatibility == JavaVersion.VERSION_1_6
        ext.sourceCompatibility == JavaVersion.VERSION_1_6
        ext.targetCompatibility == JavaVersion.VERSION_1_6

        when:
        ext.sourceCompatibility = JavaVersion.VERSION_1_8

        then:
        project.sourceCompatibility == JavaVersion.VERSION_1_8
        project.targetCompatibility == JavaVersion.VERSION_1_8
        ext.sourceCompatibility == JavaVersion.VERSION_1_8
        ext.targetCompatibility == JavaVersion.VERSION_1_8

        when:
        project.targetCompatibility = JavaVersion.VERSION_1_7

        then:
        project.sourceCompatibility == JavaVersion.VERSION_1_8
        project.targetCompatibility == JavaVersion.VERSION_1_7
        ext.sourceCompatibility == JavaVersion.VERSION_1_8
        ext.targetCompatibility == JavaVersion.VERSION_1_7

        when:
        ext.targetCompatibility = JavaVersion.VERSION_1_6

        then:
        project.sourceCompatibility == JavaVersion.VERSION_1_8
        project.targetCompatibility == JavaVersion.VERSION_1_6
        ext.sourceCompatibility == JavaVersion.VERSION_1_8
        ext.targetCompatibility == JavaVersion.VERSION_1_6
    }

    def "creates tasks and applies mappings for source set"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        new TestFile(project.file("src/custom/java/File.java")) << "foo"
        new TestFile(project.file("src/custom/resources/resource.txt")) << "foo"

        then:
        SourceSet set = project.sourceSets.custom
        set.java.srcDirs == toLinkedSet(project.file('src/custom/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/custom/resources'))
        set.java.destinationDirectory.set(new File(project.buildDir, 'classes/java/custom'))
        set.output.resourcesDir == new File(project.buildDir, 'resources/custom')
        set.output.generatedSourcesDirs.files == toLinkedSet(new File(project.buildDir, 'generated/sources/annotationProcessor/java/custom'))

        def processResources = project.tasks['processCustomResources']
        processResources.description == "Processes custom resources."
        processResources instanceof Copy
        TaskDependencyMatchers.dependsOn().matches(processResources)
        processResources.destinationDir == new File(project.buildDir, 'resources/custom')
        def resources = processResources.source
        resources.files == project.sourceSets.custom.resources.files

        def compileJava = project.tasks['compileCustomJava']
        compileJava.description == "Compiles custom Java source."
        compileJava instanceof JavaCompile
        TaskDependencyMatchers.dependsOn().matches(compileJava)
        compileJava.classpath.is(project.sourceSets.custom.compileClasspath)
        compileJava.destinationDir == new File(project.buildDir, 'classes/java/custom')

        def sources = compileJava.source
        sources.files == project.sourceSets.custom.java.files

        def classes = project.tasks['customClasses']
        classes.description == "Assembles custom classes."
        classes instanceof DefaultTask
        TaskDependencyMatchers.dependsOn('processCustomResources', 'compileCustomJava').matches(classes)
        TaskDependencyMatchers.builtBy('customClasses', 'compileCustomJava').matches(project.sourceSets.custom.output)
    }

    def "creates tasks and applies mappings for main source set"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('main')

        then:
        SourceSet set = project.sourceSets.main
        set.java.srcDirs == toLinkedSet(project.file('src/main/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/main/resources'))
        set.java.destinationDirectory.set(new File(project.buildDir, 'classes/java/main'))
        set.output.resourcesDir == new File(project.buildDir, 'resources/main')
        set.output.generatedSourcesDirs.files == toLinkedSet(new File(project.buildDir, 'generated/sources/annotationProcessor/java/main'))

        def processResources = project.tasks.processResources
        processResources.description == "Processes main resources."
        processResources instanceof Copy

        def compileJava = project.tasks.compileJava
        compileJava.description == "Compiles main Java source."
        compileJava instanceof JavaCompile

        def classes = project.tasks.classes
        classes.description == "Assembles main classes."
        TaskDependencyMatchers.dependsOn('processResources', 'compileJava').matches(classes)
    }

    def "wires generated resources task into classes task for sourceset"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')

        and:
        final someTask = project.task("someTask")
        project.sourceSets.custom.output.dir('some-dir', builtBy: someTask)

        then:
        def customClasses = project.tasks['customClasses']
        TaskDependencyMatchers.dependsOn('someTask', 'processCustomResources', 'compileCustomJava').matches(customClasses)
    }

    def "wires toolchain for sourceset if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        project.sourceSets.create('custom')

        then:
        def compileTask = project.tasks.named("compileCustomJava", JavaCompile).get()
        def configuredToolchain = compileTask.javaCompiler.get().javaToolchain
        configuredToolchain.displayName.contains(someJdk.javaVersion.getMajorVersion())
    }

    def "source and target compatibility are configured if toolchain is configured"() {
        given:
        setupProjectWithToolchain(Jvm.current().javaVersion)

        when:
        project.sourceSets.create('custom')

        then:
        JavaVersion.toVersion(project.tasks.compileJava.getSourceCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileJava.getTargetCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileCustomJava.getSourceCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileCustomJava.getTargetCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
    }

    def "wires toolchain for test if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        def testTask = project.tasks.named("test", Test).get()
        def configuredJavaLauncher = testTask.javaLauncher.get()

        then:
        configuredJavaLauncher.executablePath.toString().contains(someJdk.javaVersion.getMajorVersion())
    }

    def "wires toolchain for javadoc if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        def javadocTask = project.tasks.named("javadoc", Javadoc).get()
        def configuredJavadocTool = javadocTask.javadocTool

        then:
        configuredJavadocTool.isPresent()
    }

    def 'can set java compile source compatibility if toolchain is configured'() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)
        def prevJavaVersion = JavaVersion.toVersion(someJdk.javaVersion.majorVersion.toInteger() - 1)
        project.java.sourceCompatibility = prevJavaVersion

        when:
        def javaCompileTask = project.tasks.named("compileJava", JavaCompile).get()

        then:
        javaCompileTask.sourceCompatibility == prevJavaVersion.toString()
        javaCompileTask.sourceCompatibility == prevJavaVersion.toString()
    }

    private void setupProjectWithToolchain(JavaVersion version) {
        project.pluginManager.apply(JavaPlugin)
        project.java.toolchain.languageVersion = JavaLanguageVersion.of(version.majorVersion)
    }

    def "tasks reflect changes to source set configuration"() {
        def classesDir = project.file('target/classes')
        def resourcesDir = project.file('target/resources')

        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        project.sourceSets.custom.java.destinationDirectory.set(classesDir)
        project.sourceSets.custom.output.resourcesDir = resourcesDir

        then:
        def processResources = project.tasks['processCustomResources']
        processResources.destinationDir == resourcesDir

        def compileJava = project.tasks['compileCustomJava']
        compileJava.destinationDirectory.get().getAsFile() == classesDir
    }

    def "sourceSet reflect changes to tasks configuration"() {
        def generatedSourcesDir = project.file('target/generated-sources')

        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        def compileJava = project.tasks['compileCustomJava'] as JavaCompile
        compileJava.options.annotationProcessorGeneratedSourcesDirectory = generatedSourcesDir

        then:
        project.sourceSets.custom.output.generatedSourcesDirs.files == toLinkedSet(generatedSourcesDir)
    }

    def "creates configurations for new sourceSet"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def sourceSet = project.sourceSets.create('custom')

        then:
        def implementation = project.configurations.customImplementation
        !implementation.visible
        implementation.extendsFrom == [] as Set
        implementation.description == "Implementation only dependencies for source set 'custom'."
        !implementation.canBeConsumed
        !implementation.canBeResolved

        and:
        def runtimeOnly = project.configurations.customRuntimeOnly
        runtimeOnly.transitive
        !runtimeOnly.visible
        !runtimeOnly.canBeConsumed
        !runtimeOnly.canBeResolved
        runtimeOnly.extendsFrom == [] as Set
        runtimeOnly.description == "Runtime only dependencies for source set 'custom'."

        and:
        def runtimeClasspath = project.configurations.customRuntimeClasspath
        runtimeClasspath.transitive
        !runtimeClasspath.visible
        !runtimeClasspath.canBeConsumed
        runtimeClasspath.canBeResolved
        runtimeClasspath.extendsFrom == [runtimeOnly, implementation] as Set
        runtimeClasspath.description == "Runtime classpath of source set 'custom'."

        and:
        def compileOnly = project.configurations.customCompileOnly
        compileOnly.transitive
        !compileOnly.visible
        compileOnly.extendsFrom == [] as Set
        compileOnly.description == "Compile only dependencies for source set 'custom'."

        and:
        def compileClasspath = project.configurations.customCompileClasspath
        compileClasspath.transitive
        !compileClasspath.visible
        compileClasspath.extendsFrom == [compileOnly, implementation] as Set
        compileClasspath.description == "Compile classpath for source set 'custom'."

        and:
        def sourceSetRuntimeClasspath = sourceSet.runtimeClasspath
        def sourceSetCompileClasspath = sourceSet.compileClasspath
        sourceSetCompileClasspath == compileClasspath
        sourceSetRuntimeClasspath sameCollection(sourceSet.output + runtimeClasspath)
    }

    def "applies mappings to tasks defined by build script"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def compile = project.task('customCompile', type: JavaCompile)
        compile.sourceCompatibility == project.sourceCompatibility.toString()

        def test = project.task('customTest', type: Test.class)
        test.workingDir == project.projectDir
        test.reports.junitXml.outputLocation.get().asFile == new File(project.testResultsDir, 'customTest')
        test.reports.html.outputLocation.get().asFile == new File(project.testReportDir, 'customTest')
        test.reports.junitXml.required.get()
        test.reports.html.required.get()

        def javadoc = project.task('customJavadoc', type: Javadoc)
        javadoc.destinationDir == project.file("$project.docsDir/javadoc")
        javadoc.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    def "applies mappings to custom jar tasks"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.task('customJar', type: Jar)

        then:
        TaskDependencyMatchers.dependsOn().matches(task)
        task.destinationDirectory.get().asFile == project.libsDirectory.get().asFile
    }

    def "creates lifecycle build tasks"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def build = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.CHECK_TASK_NAME, BasePlugin.ASSEMBLE_TASK_NAME).matches(build)

        def buildDependent = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildDependent)

        def buildNeeded = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildNeeded)
    }

    def "check Java usage compatibility rules (consumer value=#consumer, producer value=#producer, compatible=#compatible)"() {
        given:
        JavaEcosystemSupport.UsageCompatibilityRules rules = new JavaEcosystemSupport.UsageCompatibilityRules()
        def details = Mock(CompatibilityCheckDetails)
        when:
        rules.execute(details)

        then:
        1 * details.getConsumerValue() >> Stub(Usage) { getName() >> consumer }
        1 * details.getProducerValue() >> Stub(Usage) { getName() >> producer }
        if (producer == consumer) {
            // implementations are NOT required to say "compatible" because
            // they should not even be called in this case
            0 * details._()
        } else if (compatible) {
            1 * details.compatible()
        } else {
            0 * details._()
        }

        where:
        consumer                     | producer                     | compatible
        Usage.JAVA_API               | Usage.JAVA_API               | true
        Usage.JAVA_API               | Usage.JAVA_RUNTIME           | true

        Usage.JAVA_RUNTIME           | Usage.JAVA_API               | false
        Usage.JAVA_RUNTIME           | Usage.JAVA_RUNTIME           | true
    }

    def "configures destinationDirectory for jar tasks"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.version = '1.0'

        then:
        def someJar = project.tasks.create('someJar', Jar)
        someJar.destinationDirectory.get().asFile == project.libsDirectory.get().asFile
    }

    @Issue("gradle/gradle#8700")
    def "check default disambiguation rules (consumer=#consumer, candidates=#candidates, selected=#preferred)"() {
        given:
        JavaEcosystemSupport.UsageDisambiguationRules rules = new JavaEcosystemSupport.UsageDisambiguationRules(
            usage(Usage.JAVA_API),
            usage(JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS),
            usage(Usage.JAVA_RUNTIME),
            usage(JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS)
        )
        MultipleCandidatesDetails details = new DefaultMultipleCandidateResult(usage(consumer), candidates.collect { usage(it)} as Set)

        when:
        rules.execute(details)

        then:
        details.hasResult()
        !details.matches.empty
        details.matches == [usage(preferred)] as Set

        details

        where: // not exhaustive, tests pathological cases
        consumer                | candidates                                     | preferred
        Usage.JAVA_API          | [Usage.JAVA_API, Usage.JAVA_RUNTIME]           | Usage.JAVA_API
        Usage.JAVA_RUNTIME      | [Usage.JAVA_RUNTIME, Usage.JAVA_API]           | Usage.JAVA_RUNTIME
    }

    private Usage usage(String value) {
        TestUtil.objectFactory().named(Usage, value)
    }
}
