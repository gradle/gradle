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

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.execution.ResidualProgram.Dynamic
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction
import org.gradle.kotlin.dsl.execution.ResidualProgram.Static
import org.gradle.kotlin.dsl.support.CompiledKotlinBuildScript
import org.gradle.kotlin.dsl.support.CompiledKotlinBuildscriptAndPluginsBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinBuildscriptBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinInitScript
import org.gradle.kotlin.dsl.support.CompiledKotlinInitscriptBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinPluginsBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsBuildscriptBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsPluginManagementBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsScript
import org.gradle.kotlin.dsl.support.ImplicitReceiver
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.ASTORE
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.DUP
import org.gradle.kotlin.dsl.support.bytecode.GETSTATIC
import org.gradle.kotlin.dsl.support.bytecode.ICONST_0
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESPECIAL
import org.gradle.kotlin.dsl.support.bytecode.INVOKESTATIC
import org.gradle.kotlin.dsl.support.bytecode.INVOKEVIRTUAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.NEW
import org.gradle.kotlin.dsl.support.bytecode.POP
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.TRY_CATCH
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.loadByteArray
import org.gradle.kotlin.dsl.support.bytecode.publicClass
import org.gradle.kotlin.dsl.support.bytecode.publicDefaultConstructor
import org.gradle.kotlin.dsl.support.bytecode.publicMethod
import org.gradle.kotlin.dsl.support.compileKotlinScriptToDirectory
import org.gradle.kotlin.dsl.support.messageCollectorFor
import org.gradle.kotlin.dsl.support.scriptDefinitionFromTemplate
import org.gradle.plugin.management.internal.MultiPluginRequests
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Type
import org.slf4j.Logger
import java.io.File
import kotlin.reflect.KClass


internal
typealias CompileBuildOperationRunner = (String, String, () -> String) -> String


/**
 * Compiles the given [residual program][ResidualProgram] to an [ExecutableProgram] subclass named `Program`
 * stored in the given [outputDir].
 */
internal
class ResidualProgramCompiler(
    private val outputDir: File,
    private val jvmTarget: JavaVersion,
    private val classPath: ClassPath = ClassPath.EMPTY,
    private val originalSourceHash: HashCode,
    private val programKind: ProgramKind,
    private val programTarget: ProgramTarget,
    private val implicitImports: List<String> = emptyList(),
    private val logger: Logger = interpreterLogger,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val compileBuildOperationRunner: CompileBuildOperationRunner = { _, _, action -> action() },
    private val stage1BlocksAccessorsClassPath: ClassPath = ClassPath.EMPTY,
    private val packageName: String? = null,
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
        is Instruction.CollectProjectScriptDependencies -> emitCollectProjectScriptDependencies(instruction.script)
        is Instruction.ApplyBasePlugins -> emitApplyBasePluginsTo()
        is Instruction.ApplyDefaultPluginRequests -> emitApplyEmptyPluginRequestsTo()
        is Instruction.ApplyPluginRequests -> emitApplyPluginRequests(instruction.requests, instruction.source)
        is Instruction.ApplyPluginRequestsOf -> {
            when (val program = instruction.program) {
                is Program.Plugins -> emitCompiledPluginsBlock(program)
                is Program.PluginManagement -> emitStage1Sequence(program)
                is Program.Stage1Sequence -> emitStage1Sequence(program.pluginManagement, program.buildscript, program.plugins)
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
    fun MethodVisitor.emitCollectProjectScriptDependencies(source: ProgramSource) {
        val scriptDefinition = stage1ScriptDefinition
        val compiledScriptClass = compileStage1(source, scriptDefinition, classPath + stage1BlocksClassPath)
        emitInstantiationOfCompiledScriptClass(compiledScriptClass, scriptDefinition)
    }

    private
    fun MethodVisitor.emitEval(source: ProgramSource) {
        val scriptDefinition = stage1ScriptDefinition
        val compiledScriptClass = compileStage1(source, scriptDefinition)
        emitInstantiationOfCompiledScriptClass(compiledScriptClass, scriptDefinition)
    }

    private
    val Program.Stage1.fragment: ProgramSourceFragment
        get() = when (this) {
            is Program.Buildscript -> fragment
            is Program.Plugins -> fragment
            is Program.PluginManagement -> fragment
            else -> TODO("Unsupported fragment: $fragment")
        }

    private
    fun MethodVisitor.emitStage1Sequence(vararg stage1Seq: Program.Stage1?) {
        emitStage1Sequence(listOfNotNull(*stage1Seq))
    }

    private
    fun MethodVisitor.emitStage1Sequence(stage1Seq: List<Program.Stage1>) {
        val scriptDefinition = buildscriptWithPluginsScriptDefinition
        val plugins = stage1Seq.filterIsInstance<Program.Plugins>().singleOrNull()
        val firstElement = stage1Seq.first()
        val compiledBuildscriptWithPluginsBlock =
            compileStage1(
                firstElement.fragment.source.map {
                    it.preserve(stage1Seq.map { stage1 -> stage1.fragment.range })
                },
                scriptDefinition,
                stage1BlocksClassPath
            )

        val implicitReceiverType = implicitReceiverOf(scriptDefinition)!!
        compiledScriptClassInstantiation(compiledBuildscriptWithPluginsBlock) {

            emitPluginRequestCollectorInstantiation()

            NEW(compiledBuildscriptWithPluginsBlock)
            ALOAD(Vars.ScriptHost)
            // ${plugins}(temp.createSpec(lineNumber))
            emitPluginRequestCollectorCreateSpecFor(plugins)
            loadTargetOf(implicitReceiverType)
            INVOKESPECIAL(
                compiledBuildscriptWithPluginsBlock,
                "<init>",
                "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/plugin/use/PluginDependenciesSpec;L${implicitReceiverType.internalName};)V"
            )

            emitApplyPluginsTo()
        }
    }

    private
    fun MethodVisitor.emitApplyPluginRequests(
        pluginRequestSpecs: List<ResidualProgram.PluginRequestSpec>,
        source: Program.Plugins?
    ) {
        emitPluginRequestCollectorInstantiation()
        emitPluginRequestCollectorCreateSpecFor(source)
        emitPluginRequests(pluginRequestSpecs)
        emitApplyPluginsTo()
    }

    private
    fun MethodVisitor.emitPluginRequests(specs: List<ResidualProgram.PluginRequestSpec>) {
        specs.forEach { spec ->
            DUP()
            LDC(spec.id)
            INVOKEINTERFACE(
                InternalName("org/gradle/plugin/use/PluginDependenciesSpec"),
                "id",
                "(Ljava/lang/String;)Lorg/gradle/plugin/use/PluginDependencySpec;"
            )
            if (spec.version != null) {
                LDC(spec.version)
                INVOKEINTERFACE(
                    InternalName("org/gradle/plugin/use/PluginDependencySpec"),
                    "version",
                    "(Ljava/lang/String;)Lorg/gradle/plugin/use/PluginDependencySpec;"
                )
            }
            if (!spec.apply) {
                ICONST_0()
                INVOKEINTERFACE(
                    InternalName("org/gradle/plugin/use/PluginDependencySpec"),
                    "apply",
                    "(Z)Lorg/gradle/plugin/use/PluginDependencySpec;"
                )
            }
            POP()
        }
        POP()
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
        loadTargetOf(Project::class)
        invokeHost(
            "applyBasePluginsTo",
            "(Lorg/gradle/api/Project;)V"
        )
    }

    private
    fun MethodVisitor.loadTargetOf(expectedType: KClass<*>) {
        ALOAD(Vars.ScriptHost)
        INVOKEVIRTUAL(
            KotlinScriptHost::class.internalName,
            "getTarget",
            "()Ljava/lang/Object;"
        )
        CHECKCAST(expectedType.internalName)
    }

    private
    fun MethodVisitor.emitApplyEmptyPluginRequestsTo() {
        ALOAD(Vars.ProgramHost)
        ALOAD(Vars.ScriptHost)
        GETSTATIC(
            MultiPluginRequests::class.internalName,
            "EMPTY",
            "Lorg/gradle/plugin/management/internal/PluginRequests;"
        )
        invokeApplyPluginsTo()
    }

    fun emitStage2ProgramFor(scriptFile: File, originalPath: String) {

        val scriptDefinition = stage2ScriptDefinition
        val compiledScriptClass = compileScript(
            scriptFile,
            originalPath,
            scriptDefinition,
            StableDisplayNameFor.stage2
        )

        program<ExecutableProgram> {

            overrideExecute {

                emitInstantiationOfCompiledScriptClass(compiledScriptClass, scriptDefinition)
            }
        }
    }

    private
    fun MethodVisitor.emitCompiledPluginsBlock(program: Program.Plugins) {

        val compiledPluginsBlock = compilePlugins(program)

        compiledScriptClassInstantiation(compiledPluginsBlock) {

            /*
             * val collector = PluginRequestCollector(kotlinScriptHost.scriptSource)
             */
            emitPluginRequestCollectorInstantiation()

            // ${compiledPluginsBlock}(collector.createSpec(lineNumber))
            NEW(compiledPluginsBlock)
            ALOAD(Vars.ScriptHost)
            emitPluginRequestCollectorCreateSpecFor(program)
            INVOKESPECIAL(
                compiledPluginsBlock,
                "<init>",
                "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/plugin/use/PluginDependenciesSpec;)V"
            )

            emitApplyPluginsTo()
        }
    }

    /**
     * val collector = PluginRequestCollector(kotlinScriptHost.scriptSource)
     */
    private
    fun MethodVisitor.emitPluginRequestCollectorInstantiation() {
        NEW(pluginRequestCollectorType)
        DUP()
        ALOAD(Vars.ScriptHost)
        INVOKEVIRTUAL(
            KotlinScriptHost::class.internalName,
            "getScriptSource",
            "()Lorg/gradle/groovy/scripts/ScriptSource;"
        )
        INVOKESPECIAL(
            pluginRequestCollectorType,
            "<init>",
            "(Lorg/gradle/groovy/scripts/ScriptSource;)V"
        )
        ASTORE(Vars.PluginRequestCollector)
    }

    private
    fun MethodVisitor.emitPluginRequestCollectorGetPluginRequests() {
        ALOAD(Vars.PluginRequestCollector)
        INVOKEVIRTUAL(
            pluginRequestCollectorType,
            "getPluginRequests",
            "()Lorg/gradle/plugin/management/internal/PluginRequests;"
        )
    }

    private
    fun MethodVisitor.emitPluginRequestCollectorCreateSpecFor(plugins: Program.Plugins?) {
        ALOAD(Vars.PluginRequestCollector)
        LDC(plugins?.fragment?.lineNumber ?: 0)
        INVOKEVIRTUAL(
            pluginRequestCollectorType,
            "createSpec",
            "(I)Lorg/gradle/plugin/use/PluginDependenciesSpec;"
        )
    }

    private
    val pluginRequestCollectorType = PluginRequestCollector::class.internalName

    private
    fun MethodVisitor.invokeApplyPluginsTo() {
        invokeHost(
            "applyPluginsTo",
            "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/plugin/management/internal/PluginRequests;)V"
        )
    }

    private
    fun ClassWriter.overrideLoadSecondStageFor() {
        publicMethod(
            name = "loadSecondStageFor",
            desc = loadSecondStageForDescriptor
        ) {
            ALOAD(Vars.ProgramHost)
            ALOAD(0)
            ALOAD(Vars.ScriptHost)
            ALOAD(3)
            GETSTATIC(programKind)
            GETSTATIC(programTarget)
            ALOAD(4)
            invokeHost(
                ExecutableProgram.Host::compileSecondStageOf.name,
                compileSecondStageOfDescriptor
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
        if (requiresAccessors()) emitAccessorsClassPathForScriptHost() else GETSTATIC(ClassPath::EMPTY)
        invokeHost(
            ExecutableProgram.Host::evaluateSecondStageOf.name,
            "(" +
                stagedProgramType +
                "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;" +
                "Ljava/lang/String;" +
                "Lorg/gradle/internal/hash/HashCode;" +
                "Lorg/gradle/internal/classpath/ClassPath;" +
                ")V"
        )
    }

    private
    val stagedProgram = Type.getType(ExecutableProgram.StagedProgram::class.java)

    private
    val stagedProgramType = stagedProgram.descriptor

    private
    val loadSecondStageForDescriptor = Type.getMethodDescriptor(
        Type.getType(CompiledScript::class.java),
        Type.getType(ExecutableProgram.Host::class.java),
        Type.getType(KotlinScriptHost::class.java),
        Type.getType(ProgramId::class.java),
        Type.getType(ClassPath::class.java)
    )

    private
    val compileSecondStageOfDescriptor = Type.getMethodDescriptor(
        Type.getType(CompiledScript::class.java),
        stagedProgram,
        Type.getType(KotlinScriptHost::class.java),
        Type.getType(ProgramId::class.java),
        Type.getType(ProgramKind::class.java),
        Type.getType(ProgramTarget::class.java),
        Type.getType(ClassPath::class.java)
    )

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
            stage1BlocksClassPath
        )

    private
    val stage1BlocksClassPath
        get() = classPath + stage1BlocksAccessorsClassPath

    private
    fun MethodVisitor.loadHashCode(hashCode: HashCode) {
        loadByteArray(hashCode.toByteArray())
        INVOKESTATIC(
            HashCode::class.internalName,
            "fromBytes",
            "([B)Lorg/gradle/internal/hash/HashCode;"
        )
    }

    private
    fun MethodVisitor.emitInstantiationOfCompiledScriptClass(
        compiledScriptClass: InternalName,
        scriptDefinition: ScriptDefinition
    ) {

        val implicitReceiverType = implicitReceiverOf(scriptDefinition)
        compiledScriptClassInstantiation(compiledScriptClass) {

            // ${compiledScriptClass}(scriptHost)
            NEW(compiledScriptClass)
            val constructorSignature =
                if (implicitReceiverType != null) {
                    ALOAD(Vars.ScriptHost)
                    loadTargetOf(implicitReceiverType)
                    "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;L${implicitReceiverType.internalName};)V"
                } else {
                    ALOAD(Vars.ScriptHost)
                    "(Lorg/gradle/kotlin/dsl/support/KotlinScriptHost)V"
                }
            INVOKESPECIAL(compiledScriptClass, "<init>", constructorSignature)
        }
    }

    private
    fun MethodVisitor.compiledScriptClassInstantiation(compiledScriptClass: InternalName, instantiation: MethodVisitor.() -> Unit) {

        TRY_CATCH<Throwable>(
            tryBlock = {

                instantiation()
            },
            catchBlock = {

                emitOnScriptException(compiledScriptClass)
            }
        )
    }

    private
    fun MethodVisitor.emitOnScriptException(compiledScriptClass: InternalName) {
        // Exception is on the stack
        ASTORE(4)
        ALOAD(Vars.ProgramHost)
        ALOAD(4)
        LDC(Type.getType("L$compiledScriptClass;"))
        ALOAD(Vars.ScriptHost)
        invokeHost(
            "handleScriptException",
            "(Ljava/lang/Throwable;Ljava/lang/Class;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;)V"
        )
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
        writeFile(
            "Program.class",
            publicClass(InternalName("Program"), superName, null) {
                publicDefaultConstructor(superName)
                classBody()
            }
        )
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
        scriptDefinition: ScriptDefinition,
        compileClassPath: ClassPath = classPath
    ): InternalName =
        temporaryFileProvider.withTemporaryScriptFileFor(source.path, source.text) { scriptFile ->
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
        scriptDefinition: ScriptDefinition,
        stage: String,
        compileClassPath: ClassPath = classPath
    ) = InternalName.from(
        compileBuildOperationRunner(originalPath, stage) {
            compileKotlinScriptToDirectory(
                outputDir,
                jvmTarget,
                scriptFile,
                scriptDefinition,
                compileClassPath.asFiles,
                messageCollectorFor(logger) { path ->
                    if (path == scriptFile.path) originalPath
                    else path
                }
            )
        }.let { compiledScriptClassName ->
            packageName
                ?.let { "$it.$compiledScriptClassName" }
                ?: compiledScriptClassName
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
                ProgramTarget.Project -> CompiledKotlinBuildscriptBlock::class
                ProgramTarget.Settings -> CompiledKotlinSettingsBuildscriptBlock::class
                ProgramTarget.Gradle -> CompiledKotlinInitscriptBlock::class
            }
        )

    private
    val stage2ScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> CompiledKotlinBuildScript::class
                ProgramTarget.Settings -> CompiledKotlinSettingsScript::class
                ProgramTarget.Gradle -> CompiledKotlinInitScript::class
            }
        )

    private
    val pluginsScriptDefinition
        get() = scriptDefinitionFromTemplate(CompiledKotlinPluginsBlock::class)

    private
    fun implicitReceiverOf(scriptDefinition: ScriptDefinition): KClass<*>? =
        implicitReceiverOf(scriptDefinition.baseClassType.fromClass!!)

    private
    val buildscriptWithPluginsScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> CompiledKotlinBuildscriptAndPluginsBlock::class
                ProgramTarget.Settings -> CompiledKotlinSettingsPluginManagementBlock::class
                else -> TODO("Unsupported program target: `$programTarget`")
            }
        )

    private
    fun scriptDefinitionFromTemplate(template: KClass<out Any>) =
        scriptDefinitionFromTemplate(
            template,
            implicitImports,
            implicitReceiverOf(template),
            classPath.asFiles
        )

    private
    fun implicitReceiverOf(template: KClass<*>) =
        template.annotations.filterIsInstance<ImplicitReceiver>().map { it.type }.firstOrNull()
}


internal
fun requiresAccessors(programTarget: ProgramTarget, programKind: ProgramKind) =
    programTarget == ProgramTarget.Project && programKind == ProgramKind.TopLevel
