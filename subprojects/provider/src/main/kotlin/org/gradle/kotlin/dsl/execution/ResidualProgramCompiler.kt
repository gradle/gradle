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

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinSettingsScript
import org.gradle.kotlin.dsl.support.KotlinBuildscriptBlock
import org.gradle.kotlin.dsl.support.KotlinSettingsBuildscriptBlock
import org.gradle.kotlin.dsl.support.compileKotlinScriptToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.messageCollectorFor

import org.jetbrains.kotlin.script.KotlinScriptDefinition

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.T_BYTE
import org.jetbrains.org.objectweb.asm.Type

import org.slf4j.Logger

import java.io.File

import kotlin.reflect.KClass

import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies


internal
class ResidualProgramCompiler(
    private val outputDir: File,
    private val classPath: ClassPath = ClassPath.EMPTY,
    private val originalSourceHash: HashCode,
    private val programKind: ProgramKind,
    private val programTarget: ProgramTarget,
    private val implicitImports: List<String> = emptyList(),
    private val logger: Logger = loggerFor<Interpreter>()
) {

    /**
     * Compiles the given residual [program] to an [ExecutableProgram] subclass named `Program`
     * stored in the given [outputDir].
     */
    fun compile(program: Program) {
        when (program) {
            is Program.Empty -> emitEmptyProgram()
            is Program.Buildscript -> emitStage1Program(program)
            is Program.Script -> emitScriptProgram(program)
            is Program.PrecompiledScript -> emitPrecompiledScriptPluginProgram(program)
            is Program.Staged -> emitStagedProgram(program)
            else -> throw IllegalArgumentException("Unsupported program `$program'")
        }
    }

    private
    fun emitEmptyProgram() {
        // TODO: consider caching the empty program bytes
        program<ExecutableProgram.Empty>()
    }

    private
    fun emitStage1Program(program: Program.Buildscript) {
        val precompiledScriptClassName = compileBuildscript(program)
        emitPrecompiledStage1Program(precompiledScriptClassName)
    }

    private
    fun emitScriptProgram(program: Program.Script) {
        emitStagedProgram(null, program)
    }

    private
    fun emitStagedProgram(program: Program.Staged) {
        val (stage1, stage2) = program
        val buildscript = stage1 as Program.Buildscript
        val precompiledScriptClassName = compileBuildscript(buildscript)
        emitStagedProgram(precompiledScriptClassName, stage2)
    }

    private
    fun emitPrecompiledScriptPluginProgram(program: Program.PrecompiledScript) {
        val source = program.source
        val scriptFile = scriptFileFor(source)
        val scriptPath = source.path
        val precompiledScriptClass = compileScript(scriptFile, scriptPath, stage1ScriptDefinition)
        emitPrecompiledScriptPluginProgram(precompiledScriptClass)
    }

    private
    fun emitStagedProgram(stage1PrecompiledScript: String?, stage2: Program.Script) {
        val source = stage2.source
        val scriptFile = scriptFileFor(source)
        val originalPath = source.path
        emitStagedProgram(stage1PrecompiledScript, scriptFile.canonicalPath, originalPath)
    }

    fun emitStage2ProgramFor(scriptFile: File, originalPath: String) {
        val precompiledScriptClass = compileScript(scriptFile, originalPath, stage1ScriptDefinition)
        emitPrecompiledStage2Program(precompiledScriptClass)
    }

    private
    fun emitPrecompiledStage1Program(precompiledScriptClass: String) {

        program<ExecutableProgram> {

            overrideExecute {

                emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass)
                emitCloseTargetScopeOf()
            }
        }
    }

    private
    fun emitPrecompiledStage2Program(precompiledScriptClass: String) {

        program<ExecutableProgram> {

            overrideExecute {

                emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass)
            }
        }
    }

    private
    fun emitPrecompiledScriptPluginProgram(precompiledScriptClass: String) {

        program<ExecutableProgram> {

            overrideExecute {

                emitCloseTargetScopeOf()
                emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass)
            }
        }
    }

    private
    fun emitStagedProgram(
        stage1PrecompiledScript: String?,
        sourceFilePath: String,
        originalPath: String
    ) {

        program<ExecutableProgram.StagedProgram> {

            overrideExecute {

                stage1PrecompiledScript?.let {
                    emitInstantiationOfPrecompiledScriptClass(it)
                }

                emitCloseTargetScopeOf()
                emitEvaluateSecondStageOf()
            }

            publicMethod(
                "loadSecondStageFor",
                "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Ljava/lang/String;Lorg/gradle/internal/hash/HashCode;)Ljava/lang/Class;",
                "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost<*>;Ljava/lang/String;Lorg/gradle/internal/hash/HashCode;)Ljava/lang/Class<*>;"
            ) {

                emitCompileSecondStageOf(sourceFilePath, originalPath)
                ARETURN()
            }
        }
    }

    private
    fun MethodVisitor.emitEvaluateSecondStageOf() {
        // programHost.evaluateSecondStageOf(...)
        ALOAD(1) // programHost
        ALOAD(0) // program/this
        ALOAD(2) // scriptHost
        LDC(programTarget.name + "/" + programKind.name + "/stage2")
        // Move HashCode value to a static field so it's cached across invocations
        loadHashCode(originalSourceHash)
        invokeHost(
            ExecutableProgram.Host::evaluateSecondStageOf.name,
            "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$StagedProgram;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Ljava/lang/String;Lorg/gradle/internal/hash/HashCode;)V")
    }

    private
    fun MethodVisitor.emitCompileSecondStageOf(sourceFilePath: String, originalPath: String) {
        ALOAD(1) // programHost
        LDC(sourceFilePath)
        LDC(originalPath)
        ALOAD(2) // scriptHost
        ALOAD(3)
        ALOAD(4)
        GETSTATIC(programKind)
        GETSTATIC(programTarget)
        invokeHost(
            ExecutableProgram.Host::compileSecondStageScript.name,
            "(" +
                "Ljava/lang/String;Ljava/lang/String;" +
                "Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;" +
                "Ljava/lang/String;Lorg/gradle/internal/hash/HashCode;" +
                "Lorg/gradle/kotlin/dsl/execution/ProgramKind;" +
                "Lorg/gradle/kotlin/dsl/execution/ProgramTarget;" +
                ")Ljava/lang/Class;")
    }

    private
    fun ClassVisitor.overrideExecute(methodBody: MethodVisitor.() -> Unit) {
        publicMethod("execute", programHostToKotlinScriptHostToVoid, "(Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost<*>;)V") {
            methodBody()
            RETURN()
        }
    }

    private
    fun compileBuildscript(program: Program.Buildscript) =
        compileScript(
            program.fragment.source.map { it.preserve(program.fragment.section.wholeRange) },
            stage2ScriptDefinition)

    private
    fun MethodVisitor.loadHashCode(hashCode: HashCode) {
        loadByteArray(hashCode.toByteArray())
        INVOKESTATIC(
            HashCode::class.internalName,
            "fromBytes",
            "([B)Lorg/gradle/internal/hash/HashCode;")
    }

    private
    fun MethodVisitor.emitInstantiationOfPrecompiledScriptClass(precompiledScriptClass: String) {

        TRY_CATCH<Throwable>(
            tryBlock = {

                // ${precompiledScriptClass}(scriptHost)
                NEW(precompiledScriptClass)
                ALOAD(2) // scriptHost
                INVOKESPECIAL(precompiledScriptClass, "<init>", kotlinScriptHostToVoid)
            },
            catchBlock = {

                // Exception is on the stack
                LDC(Type.getType("L$precompiledScriptClass;"))
                ALOAD(2) // scriptHost
                ALOAD(1) // programHost
                INVOKESTATIC(
                    ExecutableProgram.Runtime::class.internalName,
                    ExecutableProgram.Runtime::onScriptException.name,
                    "(Ljava/lang/Throwable;Ljava/lang/Class;Lorg/gradle/kotlin/dsl/support/KotlinScriptHost;Lorg/gradle/kotlin/dsl/execution/ExecutableProgram\$Host;)V")
            })
    }

    private
    fun MethodVisitor.emitCloseTargetScopeOf() {
        // programHost.closeTargetScopeOf(scriptHost)
        ALOAD(1) // programHost
        ALOAD(2) // scriptHost
        invokeHost("closeTargetScopeOf", kotlinScriptHostToVoid)
    }

    private
    fun MethodVisitor.invokeHost(name: String, desc: String) {
        INVOKEINTERFACE(ExecutableProgram.Host::class.internalName, name, desc)
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
    fun program(superName: String, classBody: ClassWriter.() -> Unit = {}) {
        writeFile("Program.class",
            publicClass("Program", superName, null) {
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
    fun compileScript(source: ProgramSource, scriptDefinition: KotlinScriptDefinition): String {
        val originalPath = source.path
        val scriptFile = scriptFileFor(source)
        return compileScript(scriptFile, originalPath, scriptDefinition)
    }

    private
    fun compileScript(scriptFile: File, originalPath: String, scriptDefinition: KotlinScriptDefinition): String =
        compileKotlinScriptToDirectory(
            outputDir,
            scriptFile,
            scriptDefinition,
            classPath.asFiles,
            messageCollectorFor(logger) { path ->
                if (path == scriptFile.path) originalPath
                else path
            })

    private
    fun scriptFileFor(source: ProgramSource) =
        outputFile(scriptFileNameFor(source.path)).apply {
            writeText(source.text)
        }

    private
    fun scriptFileNameFor(scriptPath: String) = scriptPath.run {
        val index = lastIndexOf('/')
        if (index != -1) substring(index + 1, length) else substringAfterLast('\\')
    }

    private
    val stage1ScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> KotlinBuildScript::class
                ProgramTarget.Settings -> KotlinSettingsScript::class
            })

    private
    val stage2ScriptDefinition
        get() = scriptDefinitionFromTemplate(
            when (programTarget) {
                ProgramTarget.Project -> KotlinBuildscriptBlock::class
                ProgramTarget.Settings -> KotlinSettingsBuildscriptBlock::class
            })

    private
    fun scriptDefinitionFromTemplate(template: KClass<out Any>) =
        object : KotlinScriptDefinition(template) {
            override val dependencyResolver = Resolver
        }

    private
    val Resolver by lazy {
        object : DependenciesResolver {
            override fun resolve(
                scriptContents: ScriptContents,
                environment: Environment
            ): DependenciesResolver.ResolveResult =

                DependenciesResolver.ResolveResult.Success(
                    ScriptDependencies(imports = implicitImports), emptyList())
        }
    }
}


private
fun publicClass(name: String, superName: String = "java/lang/Object", interfaces: Array<String>? = null, classBody: ClassWriter.() -> Unit = {}) =
    ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES).run {
        visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, superName, interfaces)
        classBody()
        visitEnd()
        toByteArray()
    }


private
fun ClassWriter.publicDefaultConstructor(superName: String) {
    publicMethod("<init>", "()V") {
        ALOAD(0)
        INVOKESPECIAL(superName, "<init>", "()V")
        RETURN()
    }
}


private
fun ClassVisitor.publicMethod(
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    methodBody: MethodVisitor.() -> Unit
) {
    visitMethod(Opcodes.ACC_PUBLIC, name, desc, signature, exceptions).apply {
        visitCode()
        methodBody()
        visitMaxs(0, 0)
        visitEnd()
    }
}


private
fun MethodVisitor.loadByteArray(byteArray: ByteArray) {
    LDC(byteArray.size)
    NEWARRAY(T_BYTE)
    for ((i, byte) in byteArray.withIndex()) {
        DUP()
        LDC(i)
        LDC(byte)
        BASTORE()
    }
}


private
fun MethodVisitor.NEW(type: String) {
    visitTypeInsn(Opcodes.NEW, type)
}


private
fun MethodVisitor.NEWARRAY(primitiveType: Int) {
    visitIntInsn(Opcodes.NEWARRAY, primitiveType)
}


private
fun MethodVisitor.LDC(value: Any) {
    visitLdcInsn(value)
}


private
fun MethodVisitor.INVOKESPECIAL(owner: String, name: String, desc: String, itf: Boolean = false) {
    visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, desc, itf)
}


private
fun MethodVisitor.INVOKEINTERFACE(owner: String, name: String, desc: String, itf: Boolean = true) {
    visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, desc, itf)
}


private
fun MethodVisitor.INVOKESTATIC(owner: String, name: String, desc: String) {
    visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
}


private
fun MethodVisitor.BASTORE() {
    visitInsn(Opcodes.BASTORE)
}


private
fun MethodVisitor.DUP() {
    visitInsn(Opcodes.DUP)
}


private
fun MethodVisitor.ACONST_NULL() {
    visitInsn(Opcodes.ACONST_NULL)
}


private
fun MethodVisitor.ARETURN() {
    visitInsn(Opcodes.ARETURN)
}


private
fun MethodVisitor.RETURN() {
    visitInsn(Opcodes.RETURN)
}


private
fun MethodVisitor.ALOAD(`var`: Int) {
    visitVarInsn(Opcodes.ALOAD, `var`)
}


private
fun MethodVisitor.GOTO(label: Label) {
    visitJumpInsn(Opcodes.GOTO, label)
}


private
inline fun <reified T> MethodVisitor.TRY_CATCH(
    noinline tryBlock: MethodVisitor.() -> Unit,
    noinline catchBlock: MethodVisitor.() -> Unit
) =
    TRY_CATCH(T::class.internalName, tryBlock, catchBlock)


private
fun MethodVisitor.TRY_CATCH(
    exceptionType: String,
    tryBlock: MethodVisitor.() -> Unit,
    catchBlock: MethodVisitor.() -> Unit
) {

    val tryBlockStart = Label()
    val tryBlockEnd = Label()
    val catchBlockStart = Label()
    val catchBlockEnd = Label()
    visitTryCatchBlock(tryBlockStart, tryBlockEnd, catchBlockStart, exceptionType)

    visitLabel(tryBlockStart)
    tryBlock()
    GOTO(catchBlockEnd)
    visitLabel(tryBlockEnd)

    visitLabel(catchBlockStart)
    catchBlock()
    visitLabel(catchBlockEnd)
}


private
fun <T : Enum<T>> MethodVisitor.GETSTATIC(field: T) {
    val owner = field.declaringClass.internalName
    GETSTATIC(owner, field.name, "L$owner;")
}


private
fun MethodVisitor.GETSTATIC(owner: String, name: String, desc: String) {
    visitFieldInsn(Opcodes.GETSTATIC, owner, name, desc)
}


private
val KClass<*>.internalName: String
    get() = java.internalName


private
inline val Class<*>.internalName: String
    get() = Type.getInternalName(this)
