/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.instrumentation.classpostprocessing

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.DescribingAndSpec
import org.gradle.api.specs.Spec
import org.gradle.internal.classanalysis.AsmConstants
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type.getDescriptor
import org.objectweb.asm.Type.getMethodDescriptor
import org.objectweb.asm.Type.getType
import kotlin.reflect.jvm.jvmName


/**
 * This processor adds synthetic equivalents for [TaskInternal.getOnlyIf] and [AbstractTask.getOnlyIf] returning [DescribingAndSpec].
 * The method was accidentally introduced in Gradle 7.6 in place of the one returning [Spec], breaking binary compatibility.
 *
 * In the fix, the [Spec]-returning method was brought back, but clients compiled against Gradle 7.6 would be
 * broken by this, as they expect a [DescribingAndSpec]-returning method.
 *
 * Issue [#23520](https://github.com/gradle/gradle/issues/23520)
 */
object ReintroduceGetOnlyIfMethod : ClassPostProcessor {
    override val classNamesToProcess: Iterable<String>
        get() = listOf(
            TaskInternal::class,
            AbstractTask::class
        ).map { it.jvmName }


    override fun classVisitorForClass(className: String, classVisitor: ClassVisitor): ClassVisitor =
        when (className) {
            TaskInternal::class.jvmName -> TaskInternalVisitor(classVisitor)
            AbstractTask::class.jvmName -> AbstractTaskVisitor(classVisitor)
            else -> throw IllegalAccessException("unexpected class name $className")
        }

    private
    class TaskInternalVisitor(classVisitor: ClassVisitor) : ClassVisitor(AsmConstants.ASM_LEVEL, classVisitor) {
        override fun visitEnd() {
            cv.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_ABSTRACT,
                GET_ONLY_IF_NAME,
                RETURN_DESCRIBING_AND_SPEC,
                null, null
            ).visitEnd()
        }
    }

    private
    class AbstractTaskVisitor(classVisitor: ClassVisitor) : ClassVisitor(AsmConstants.ASM_LEVEL, classVisitor) {
        override fun visitEnd() {
            with(cv.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC,
                GET_ONLY_IF_NAME,
                RETURN_DESCRIBING_AND_SPEC,
                null, null
            )) {
                visitAnnotation(getDescriptor(Override::class.java), true)
                visitCode()

                // this.getOnlyIf()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, DEFAULT_TASK_TYPE.internalName, GET_ONLY_IF_NAME, RETURN_SPEC, false)

                // cast to DescribingAndSpec
                visitTypeInsn(Opcodes.CHECKCAST, DESCRIBING_AND_SPEC_TYPE.internalName)

                // return
                visitEnd()
                visitMaxs(2, 1)
                visitInsn(Opcodes.ARETURN)
            }
        }
    }

    private
    const val GET_ONLY_IF_NAME = "getOnlyIf"

    private
    val DEFAULT_TASK_TYPE = getType(AbstractTask::class.java)

    private
    val SPEC_TYPE = getType(Spec::class.java)

    private
    val DESCRIBING_AND_SPEC_TYPE = getType(DescribingAndSpec::class.java)

    private
    val RETURN_SPEC = getMethodDescriptor(SPEC_TYPE)

    private
    val RETURN_DESCRIBING_AND_SPEC = getMethodDescriptor(DESCRIBING_AND_SPEC_TYPE)
}
