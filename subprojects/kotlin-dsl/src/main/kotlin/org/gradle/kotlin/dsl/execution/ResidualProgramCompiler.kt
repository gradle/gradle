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

package org.gradle.kotlin.dsl.execution

import org.gradle.api.Project

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinInitScript
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.execution.ResidualProgram.Dynamic
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction
import org.gradle.kotlin.dsl.execution.ResidualProgram.Static

import org.gradle.kotlin.dsl.support.KotlinBuildscriptAndPluginsBlock
import org.gradle.kotlin.dsl.support.KotlinBuildscriptBlock
import org.gradle.kotlin.dsl.support.KotlinInitscriptBlock
import org.gradle.kotlin.dsl.support.KotlinPluginsBlock
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.KotlinSettingsBuildscriptBlock

import org.gradle.kotlin.dsl.support.bytecode.ACONST_NULL
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.ASTORE
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.DUP
import org.gradle.kotlin.dsl.support.bytecode.GETSTATIC
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESPECIAL
import org.gradle.kotlin.dsl.support.bytecode.INVOKESTATIC
import org.gradle.kotlin.dsl.support.bytecode.INVOKEVIRTUAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.NEW
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.TRY_CATCH
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.loadByteArray
import org.gradle.kotlin.dsl.support.bytecode.publicClass
import org.gradle.kotlin.dsl.support.bytecode.publicDefaultConstructor
import org.gradle.kotlin.dsl.support.bytecode.publicMethod

import org.gradle.kotlin.dsl.support.compileKotlinScriptToDirectory
import org.gradle.kotlin.dsl.support.messageCollectorFor

import org.gradle.plugin.management.internal.DefaultPluginRequests

import org.gradle.plugin.use.internal.PluginRequestCollector

import org.jetbrains.kotlin.script.KotlinScriptDefinition

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Type

import org.slf4j.Logger

import java.io.File

import kotlin.reflect.KClass

import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies


internal
typealias CompileBuildOperationRunner = (String, String, () -> String) -> String


/**
 * Compiles the given [residual program][ResidualProgram] to an [ExecutableProgram] subclass named `Program`
 * stored in the given [outputDir].
 */
internal
class ResidualProgramCompiler(
    private val outputDir: File,
    private val classPath: ClassPath = ClassPath.EMPTY,
    private val originalSourceHash: HashCode,
    private val programKind: ProgramKind,
    private val programTarget: ProgramTarget,
    private val implicitImports: List<String> = emptyList(),
    private val logger: Logger = interpreterLogger,
    private val compileBuildOperationRunner: CompileBuildOperationRunner = { _, _, action -> action() },
    private val pluginAccessorsClassPath: ClassPath = ClassPath.EMPTY
) {

    fun compile(program: ResidualProgram) = when (program) {
        is Static -> emitStaticProgram(program)
        is Dynamic -> emitDynamicProgram(program)
    }

    private
    fun emitStaticProgram(program: Static) {

        program<ExecutableProgram> {

            overrideExecute {
                emit(program.instructions)
            }
        }
    }

    private
    fun emitDynamicProgram(program: Dynamic) {

        program<ExecutableProgram.StagedProgram> {

            overrideExecute {

                emit(program.prelude.instructions)
                emitEvaluateSecondStageOf()
            }

            overrideGetSecondStageScriptText(program.source.text)
            overrideLoadSecondStageFor()
        }
    }

    private
    fun ClassWriter.overrideGetSecondStageScriptText(secondStageScriptText: String) {
        publicMethod(
            "getSecondStageScriptText",
            "()Ljava/lang/String;",
            "()Ljava/lang/String;"
        ) {
            if (mightBeLargerThan64KB(secondStageScriptText)) {
                // Large scripts are stored as a resource to overcome
                // the 64KB string constant limitation
                val resourcePath = storeStringToResource(secondStageScriptText)
                ALOAD(0)
                LDC(resourcePath)
                INVOKEVIRTUAL(
                    ExecutableProgram.StagedProgram::class.internalName,
                    ExecutableProgram.StagedProgram::loadScriptResource.name,
                    "(Ljava/lang/String;)Ljava/lang/String;"
                )
            } else {
                LDC(secondStageScriptText)
            }
            ARETURN()
        }
    }

    private
    fun mightBeLargerThan64KB(secondStageScriptText: String) =
        // We use a simple heuristic to avoid converting the string to bytes
        // if all code points were in UTF32, 16K code points would require 64K bytes
        secondStageScriptText.length >= 16 * 1024

    private
    fun storeStringToResource(secondStageScriptText: String): String {
        val hash = Hashing.hashString(secondStageScriptText)
        val resourcePath = "scripts/$hash.gradle.kts"
        writeResourceFile(resourcePath, secondStageScriptText)
        return resourcePath
    }

    private
    fun writeResourceFile(resourcePath: String, resourceText: String) {
        outputFile(resourcePath).apply {
            parentFile.mkdir()
            writeText(resourceText)
        }
    }

    private
    fun MethodVisitor.emit(instructions: List<Instruction>) {
        instructions.forEach {
            emit(it)
        }
    }

    private
    fun MethodVisitor.emit(instruction: Instruction) = when (instruction) {
        is Instruction.SetupEmbeddedKotlin -> emitSetupEmbeddedKotlinFor()
        is Instruction.CloseTargetScope -> emitCloseTargetScopeOf()
        is Instruction.Eval -> emitEval(instruction.script)
        is Instruction.ApplyBasePlugins -> emitApplyBasePluginsTo()
        is Instruction.ApplyDefaultPluginRequests -> emitApplyEmptyPluginRequestsTo()
        is Instruction.ApplyPluginRequestsOf -> {
            val program = instruction.program
            when (program) {
                is Program.Plugins -> emitPrecompiledPluginsBlock(program)
                is Program.Stage1Sequence -> emitStage1Sequence(program.buildscript, program.plugins)
                else -> throw IllegalStateException("Expecting a residual program with plugins, got `$program'")
            }
        }
    }

    private
    fun MethodVisitor.emitSetupEmbeddedKotlinFor() {
        // programHost.setupEmbeddedKotlinFor(scriptHost)
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        invokeHost("setupEmbeddedKotlinFor", kotlinScriptHostToVoid)
    }

    private
    fun MethodVisitor.emitEval(source: ProgramSource) {
        val precompiledScriptClass = compileStage1(source, stage1ScriptDefinition)
        emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass)
    }

    private
    fun MethodVisitor.emitStage1Sequence(buildscript: Program.Buildscript, plugins: Program.Plugins) {

        val precompiledBuildscriptWithPluginsBlock =
            compileStage1(
                plugins.fragment.source.map {
                    it.preserve(
                        buildscript.fragment.range,
                        plugins.fragment.range)
                },
                buildscriptWithPluginsScriptDefinition,
                pluginsBlockClassPath
            )

        precompiledScriptClassInstantiation(precompiledBuildscriptWithPluginsBlock) {

            emitPluginRequestCollectorInstantiation()

            NEW(precompiledBuildscriptWithPluginsBlock)
            ALOAD(Vars.ScriptHost)
            // ${plugins}(temp.createSpec(lineNumber))
            emitPluginRequestCollectorCreateSpecFor(plugins)
            INVOKESPECIAL(
                precompiledBuildscriptWithPluginsBlock,
                "<init>",
                "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/plugin/use/PluginDependenciesSpec;)V")

            emitApplyPluginsTo()
        }
    }

    /**
     * programHost.applyPluginsTo(scriptHost, collector.getPluginRequests())
     */
    private
    fun MethodVisitor.emitApplyPluginsTo() {
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        emitPluginRequestCollectorGetPluginRequests()
        invokeApplyPluginsTo()
    }

    private
    fun MethodVisitor.emitApplyBasePluginsTo() {
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        INVOKEVIRTUAL(
            KotlinScriptHost::class.internalName,
            "getTarget",
            "()Ljava/lang/Object;")
        CHECKCAST(Project::class.internalName)
        invokeHost(
            "applyBasePluginsTo",
            "(Lorg/gradle/api/Project;)V")
    }

    private
    fun MethodVisitor.emitApplyEmptyPluginRequestsTo() {
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        GETSTATIC(
            DefaultPluginRequests::class.internalName,
            "EMPTY",
            "Lorg/gradle/plugin/management/internal/PluginRequests;")
        invokeApplyPluginsTo()
    }

    fun emitStage2ProgramFor(scriptFile: File, originalPath: String) {

        val precompiledScriptClass = compileScript(
            scriptFile,
            originalPath,
            stage2ScriptDefinition,
            StableDisplayNameFor.stage2
        )

        program<ExecutableProgram> {

            overrideExecute {

                emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass)
            }
        }
    }

    private
    fun MethodVisitor.emitPrecompiledPluginsBlock(program: Program.Plugins) {

        val precompiledPluginsBlock = compilePlugins(program)

        precompiledScriptClassInstantiation(precompiledPluginsBlock) {

            // val collector = PluginRequestCollector(scriptSource)
            emitPluginRequestCollectorInstantiation()

            // ${precompiledPluginsBlock}(collector.createSpec(lineNumber))
            NEW(precompiledPluginsBlock)
            emitPluginRequestCollectorCreateSpecFor(program)
            INVOKESPECIAL(
                precompiledPluginsBlock,
                "<init>",
                "(Lorg/gradle/plugin/use/PluginDependenciesSpec;)V")

            emitApplyPluginsTo()
        }
    }

    /**
     * val collector = PluginRequestCollector(scriptSource)
     */
    private
    fun MethodVisitor.emitPluginRequestCollectorInstantiation() {
        NEW(pluginRequestCollectorType)
        DUP()
        ALOAD(Vars.ScriptHost)
        INVOKEVIRTUAL(
            KotlinScriptHost::class.internalName,
            "getScriptSource",
            "()Lorg/gradle/groovy/scripts/ScriptSource;")
        INVOKESPECIAL(
            pluginRequestCollectorType,
            "<init>",
            "(Lorg/gradle/groovy/scripts/ScriptSource;)V")
        ASTORE(Vars.PluginRequestCollector)
    }

    private
    fun MethodVisitor.emitPluginRequestCollectorGetPluginRequests() {
        ALOAD(Vars.PluginRequestCollector)
        INVOKEVIRTUAL(
            pluginRequestCollectorType,
            "getPluginRequests",
            "()Lorg/gradle/plugin/management/internal/PluginRequests;")
    }

    private
    fun MethodVisitor.emitPluginRequestCollectorCreateSpecFor(plugins: Program.Plugins) {
        ALOAD(Vars.PluginRequestCollector)
        LDC(plugins.fragment.lineNumber)
        INVOKEVIRTUAL(
            pluginRequestCollectorType,
            "createSpec",
            "(I)Lorg/gradle/plugin/use/PluginDependenciesSpec;")
    }

    private
    val pluginRequestCollectorType = PluginRequestCollector::class.internalName

    private
    fun MethodVisitor.invokeApplyPluginsTo() {
        invokeHost(
            "applyPluginsTo",
            "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/plugin/management/internal/PluginRequests;)V")
    }

    private
    fun ClassWriter.overrideLoadSecondStageFor() {
        publicMethod(
            name = "loadSecondStageFor",
            desc = "(" +
                "Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;" +
                "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;" +
                "Ljava/lang/String;" +
                "Lorg/gradle/internal/hash/HashCode;" +
                "Lorg/gradle/internal/classpath/ClassPath;" +
                ")Ljava/lang/Class;",
            signature = "(" +
                "Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;" +
                "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost<*>;" +
                "Ljava/lang/String;" +
                "Lorg/gradle/internal/hash/HashCode;" +
                "Lorg/gradle/internal/classpath/ClassPath;" +
                ")Ljava/lang/Class<*>;"
        ) {

            ALOAD(Vars.ProgramHost)
            ALOAD(0)
            ALOAD(Vars.ScriptHost)
            ALOAD(3)
            ALOAD(4)
            GETSTATIC(programKind)
            GETSTATIC(programTarget)
            ALOAD(5)
            invokeHost(
                ExecutableProgram.Host::compileSecondStageOf.name,
                "(" +
                    stagedProgramType +
                    "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;" +
                    "Ljava/lang/String;Lorg/gradle/internal/hash/HashCode;" +
                    "Lorg/gradle/kotlin/dsl/execution/ProgramKind;" +
                    "Lorg/gradle/kotlin/dsl/execution/ProgramTarget;" +
                    "Lorg/gradle/internal/classpath/ClassPath;" +
                    ")Ljava/lang/Class;"
            )
            ARETURN()
        }
    }

    private
    fun MethodVisitor.emitEvaluateSecondStageOf() {
        // programHost.evaluateSecondStageOf(...)
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.Program)
        ALOAD(Vars.ScriptHost)
        LDC(programTarget.name + "/" + programKind.name + "/stage2")
        // Move HashCode value to a static field so it's cached across invocations
        loadHashCode(originalSourceHash)
        if (requiresAccessors()) emitAccessorsClassPathForScriptHost() else ACONST_NULL()
        invokeHost(
            ExecutableProgram.Host::evaluateSecondStageOf.name,
            "(" +
                stagedProgramType +
                "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;" +
                "Ljava/lang/String;" +
                "Lorg/gradle/internal/hash/HashCode;" +
                "Lorg/gradle/internal/classpath/ClassPath;" +
                ")V")
    }

    private
    val stagedProgramType = "Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$StagedProgram;"

    private
    fun requiresAccessors() =
        requiresAccessors(programTarget, programKind)

    private
    fun MethodVisitor.emitAccessorsClassPathForScriptHost() {
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        invokeHost(
            "accessorsClassPathFor",
            "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;)Lorg/gradle/internal/classpath/ClassPath;"
        )
    }

    private
    fun ClassVisitor.overrideExecute(methodBody: MethodVisitor.() -> Unit) {
        publicMethod("execute", programHostToKotlinScriptHostToVoid, "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost<*>;)V") {
            methodBody()
            RETURN()
        }
    }

    private
    fun compilePlugins(program: Program.Plugins) =
        compileStage1(
            program.fragment.source.map { it.preserve(program.fragment.range) },
            pluginsScriptDefinition,
            pluginsBlockClassPath
        )

    private
    val pluginsBlockClassPath
        get() = classPath + pluginAccessorsClassPath

    private
    fun MethodVisitor.loadHashCode(hashCode: HashCode) {
        loadByteArray(hashCode.toByteArray())
        INVOKESTATIC(
            HashCode::class.internalName,
            "fromBytes",
            "([B)Lorg/gradle/internal/hash/HashCode;")
    }

    private
    fun MethodVisitor.emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass: InternalName) {

        precompiledScriptClassInstantiation(precompiledScriptClass) {

            // ${precompiledScriptClass}(scriptHost)
            NEW(precompiledScriptClass)
            ALOAD(Vars.ScriptHost)
            INVOKESPECIAL(precompiledScriptClass, "<init>", kotlinScriptHostToVoid)
        }
    }

    private
    fun MethodVisitor.precompiledScriptClassInstantiation(precompiledScriptClass: InternalName, instantiation: MethodVisitor.() -> Unit) {

        TRY_CATCH<Throwable>(
            tryBlock = {

                instantiation()
            },
            catchBlock = {

                emitOnScriptException(precompiledScriptClass)
            })
    }

    private
    fun MethodVisitor.emitOnScriptException(precompiledScriptClass: InternalName) {
        // Exception is on the stack
        ASTORE(4)
        ALOAD(Vars.ProgramHost)
        ALOAD(4)
        LDC(Type.getType("L$precompiledScriptClass;"))
        ALOAD(Vars.ScriptHost)
        invokeHost(
            "handleScriptException",
            "(Ljava/lang/Throwable;Ljava/lang/Class;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;)V")
    }

    private
    fun MethodVisitor.emitCloseTargetScopeOf() {
        // programHost.closeTargetScopeOf(scriptHost)
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        invokeHost("closeTargetScopeOf", kotlinScriptHostToVoid)
    }

    private
    fun MethodVisitor.invokeHost(name: String, desc: String) {
        INVOKEINTERFACE(ExecutableProgram.Host::class.internalName, name, desc)
    }

    private
    object Vars {

        const val Program = 0

        const val ProgramHost = 1

        const val ScriptHost = 2

        // Only valid within the context of `overrideExecute`
        const val PluginRequestCollector = 3
    }

    private
    val programHostToKotlinScriptHostToVoid =
        "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;)V"

    private
    val kotlinScriptHostToVoid =
        "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;)V"

    private
    inline fun <reified T : ExecutableProgram> program(noinline classBody: ClassWriter.() -> Unit = {}) {
        program(T::class.internalName, classBody)
    }

    private
    fun program(superName: InternalName, classBody: ClassWriter.() -> Unit = {}) {
        writeFile("Program.class",
            publicClass(InternalName("Program"), superName, null) {
                publicDefaultConstructor(superName)
                classBody()
            })
    }

    private
    fun writeFile(relativePath: String, bytes: ByteArray) {
        outputFile(relativePath).writeBytes(bytes)
    }

    private
    fun outputFile(relativePath: String) =
        outputDir.resolve(relativePath)

    private
    fun compileStage1(
        source: ProgramSource,
        scriptDefinition: KotlinScriptDefinition,
        compileClassPath: ClassPath = classPath
    ): InternalName =
        withTemporaryScriptFileFor(source.path, source.text) { scriptFile ->
            val originalScriptPath = source.path
            compileScript(
                scriptFile,
                originalScriptPath,
                scriptDefinition,
                StableDisplayNameFor.stage1,
                compileClassPath
            )
        }

    private
    fun compileScript(
        scriptFile: File,
        originalPath: String,
        scriptDefinition: KotlinScriptDefinition,
        stage: String,
        compileClassPath: ClassPath = classPath
    ) = InternalName(
        compileBuildOperationRunner(originalPath, stage) {
            compileKotlinScriptToDirectory(
                outputDir,
                scriptFile,
                scriptDefinition,
                compileClassPath.asFiles,
                messageCollectorFor(logger) { path ->
                    if (path == scriptFile.path) originalPath
                    else path
                }
            )
        }
    )

    /**
     * Stage descriptions for build operations.
     *
     * Changes to these constants must be coordinated with the GE team.
     */
    private
    object StableDisplayNameFor {

        const val stage1 = "CLASSPATH"

        const val stage2 = "BODY"
    }

    private
    val stage1ScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> KotlinBuildscriptBlock::class
                ProgramTarget.Settings -> KotlinSettingsBuildscriptBlock::class
                ProgramTarget.Gradle -> KotlinInitscriptBlock::class
            })

    private
    val stage2ScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> KotlinBuildScript::class
                ProgramTarget.Settings -> KotlinSettingsScript::class
                ProgramTarget.Gradle -> KotlinInitScript::class
            })

    private
    val pluginsScriptDefinition
        get() = scriptDefinitionFromTemplate(KotlinPluginsBlock::class)

    private
    val buildscriptWithPluginsScriptDefinition
        get() = scriptDefinitionFromTemplate(KotlinBuildscriptAndPluginsBlock::class)


    private
    fun scriptDefinitionFromTemplate(template: KClass<out Any>) =
        scriptDefinitionFromTemplate(template, implicitImports)
}


fun scriptDefinitionFromTemplate(
    template: KClass<out Any>,
    implicitImports: List<String>
): KotlinScriptDefinition = object : KotlinScriptDefinition(template), DependenciesResolver {

    override val dependencyResolver = this

    override fun resolve(
        scriptContents: ScriptContents,
        environment: Environment
    ): DependenciesResolver.ResolveResult = DependenciesResolver.ResolveResult.Success(
        ScriptDependencies(imports = implicitImports),
        emptyList()
    )
}


internal
fun requiresAccessors(programTarget: ProgramTarget, programKind: ProgramKind) =
    programTarget == ProgramTarget.Project && programKind == ProgramKind.TopLevel
