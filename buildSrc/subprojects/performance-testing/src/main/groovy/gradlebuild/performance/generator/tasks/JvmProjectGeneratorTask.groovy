/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator.tasks

import gradlebuild.performance.generator.TestProject
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTask
class JvmProjectGeneratorTask extends AbstractProjectGeneratorTask {

    @Internal
    boolean groovyProject
    @Internal
    boolean scalaProject
    @Internal
    boolean createTestComponent = true

    private final Closure createPackageName = { testProject, fileNumber ->
        "org.gradle.test.performance${testProject.subprojectNumber}_${(int) (fileNumber / filesPerPackage) + 1}"
    }
    private final Closure createFileName = { testProject, prefix, fileNumber ->
        "${prefix}${testProject.subprojectNumber}_${fileNumber + 1}"
    }
    private final Closure createExtendsAndImplementsClause = { testProject, prefix, fileNumber -> '' }
    private final Closure<List<Map<String, String>>> createExtraFields = { testProject, prefix, fileNumber -> [] }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection testDependencies

    Map getTaskArgs() {
        [ groovyProject: groovyProject, scalaProject: scalaProject, testComponent: createTestComponent ]
    }

    def generateRootProject() {
        super.generateRootProject()
        generateProjectDependenciesDescriptor()
        project.copy {
            into(getDestDir())
            into('lib/test') {
                from testDependencies
            }
        }
    }

    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        generateProjectSource(projectDir, "java", testProject, args)
        if (groovyProject) {
            generateProjectSource(projectDir, "groovy", testProject, args)
        }
        if (scalaProject) {
            generateProjectSource(projectDir, "scala", testProject, args)
        }
    }

    void generateProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        def classFilePrefix
        def classFileTemplate
        def testFilePrefix
        def testFileTemplate

        if (sourceLang == "groovy") {
            classFilePrefix = "ProductionGroovy"
            classFileTemplate = "Production.groovy"
            testFilePrefix = "TestGroovy"
            testFileTemplate = "Test.groovy"
        } else if (sourceLang == "scala") {
            classFilePrefix = "ProductionScala"
            classFileTemplate = "Production.scala"
            testFilePrefix = "TestScala"
            testFileTemplate = "Test.scala"
        } else {
            classFilePrefix = "Production"
            classFileTemplate = "Production.java"
            testFilePrefix = "Test"
            testFileTemplate = "Test.java"
        }
        def createPackageName = this.createPackageName.rehydrate(this, this, this).curry(testProject)
        def createFileName = this.createFileName.rehydrate(this, this, this).curry(testProject)
        def createExtendsAndImplementsClause = this.createExtendsAndImplementsClause.rehydrate(this, this, this).curry(testProject)
        def extraFields = this.createExtraFields.rehydrate(this, this, this).curry(testProject)
        testProject.sourceFiles.times {
            String packageName = createPackageName(it)
            Map classArgs = args + [
                packageName: packageName,
                productionClassName: createFileName(classFilePrefix, it).toString(),
                extendsAndImplementsClause: createExtendsAndImplementsClause(classFilePrefix, it).toString(),
                extraFields: extraFields(classFilePrefix, it)
            ]
            generateWithTemplate(projectDir, "src/main/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.productionClassName}.${sourceLang}", classFileTemplate, classArgs)
        }
        if (createTestComponent) {
            testProject.testSourceFiles.times {
                String packageName = createPackageName(it)
                Map classArgs = args + [
                    packageName               : packageName,
                    productionClassName       : createFileName(classFilePrefix, it).toString(),
                    testClassName             : createFileName(testFilePrefix, it).toString(),
                    extendsAndImplementsClause: createExtendsAndImplementsClause(classFilePrefix, it).toString(),
                    extraFields               : extraFields(classFilePrefix, it)]
                generateWithTemplate(projectDir, "src/test/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.testClassName}.${sourceLang}", testFileTemplate, classArgs)
            }
        }
    }

    // generates a descriptor which can be used in integration tests to find out easily
    // what are the dependencies between projects
    void generateProjectDependenciesDescriptor() {
        def dependencies = templateArgs.generatedDependencies
        if (dependencies) {
            new File(destDir, "generated-deps.groovy") << """[
   ${dependencies.collect {"($it.key): ${it.value}"}.join(',\n   ') }
]"""
        }
    }
}
