/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.logging

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.codehaus.groovy.transform.LogASTTransformation
import org.objectweb.asm.Opcodes
import org.codehaus.groovy.ast.expr.*

/**
 * This local transform adds a logging ability to your program using
 * Gradle {@link Logger}. Every method call on a unbound variable named <i>log</i>
 * will be mapped to a call to the logger. For this a <i>log</i> field will be
 * inserted in the class. If the field already exists the usage of this transform
 * will cause a compilation error. The method name will be used to determine
 * what to call on the logger.
 * <pre>
 * log.name(exp)
 * </pre>is mapped to
 * <pre>
 * if (log.isNameLoggable() {
 *    log.name(exp)
 * }</pre>
 * Here name is a place holder for info, debug, warning, error, etc.
 * If the expression exp is a constant or only a variable access the method call will
 * not be transformed. But this will still cause a call on the injected logger.
 *
 * @author Benjamin Muschko
 */
@java.lang.annotation.Documented
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass("org.codehaus.groovy.transform.LogASTTransformation")
public @interface Gradle {
    String value() default "log"
    Class<? extends LogASTTransformation.LoggingStrategy> loggingStrategy() default GradleLoggingStrategy

    static class GradleLoggingStrategy implements LogASTTransformation.LoggingStrategy {
        @Override
        FieldNode addLoggerFieldToClass(ClassNode classNode, String logFieldName) {
            classNode.addField(logFieldName,
                Opcodes.ACC_FINAL | Opcodes.ACC_TRANSIENT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE,
                new ClassNode("org.gradle.api.logging.Logger", Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE),
                new MethodCallExpression(
                        new ClassExpression(new ClassNode("org.gradle.api.logging.Logging", Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE)),
                        "getLogger",
                        new ClassExpression(classNode)))

        }

        @Override
        boolean isLoggingMethod(String methodName) {
            methodName.matches("quiet|error|warn|lifecycle|info|debug|trace")
        }

        @Override
        Expression wrapLoggingMethodCall(Expression logVariable, String methodName, Expression originalExpression) {
            MethodCallExpression condition = new MethodCallExpression(
                    logVariable,
                    "is" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1, methodName.length()) + "Enabled",
                    ArgumentListExpression.EMPTY_ARGUMENTS)

            new TernaryExpression(
                new BooleanExpression(condition),
                originalExpression,
                ConstantExpression.NULL)
        }
    }
}