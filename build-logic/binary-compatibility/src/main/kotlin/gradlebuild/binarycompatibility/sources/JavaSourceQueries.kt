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

package gradlebuild.binarycompatibility.sources

import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.AnnotationMemberDeclaration
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import gradlebuild.binarycompatibility.jApiClass
import gradlebuild.binarycompatibility.simpleName
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod


private
typealias PredicateVisitor = GenericVisitorAdapter<Boolean, Unit?>

private
typealias SinceVisitor = GenericVisitorAdapter<SinceTagStatus, Unit?>

internal
object JavaSourceQueries {

    fun isOverrideMethod(method: JApiMethod): JavaSourceQuery<Boolean> =
        JavaSourceQuery(
            false,
            object : PredicateVisitor() {
                override fun visit(declaration: MethodDeclaration, arg: Unit?): Boolean? {
                    if (declaration.name?.asString() == method.name && declaration.hasOverrideAnnotation) {
                        return true
                    }
                    return null
                }
            }
        )

    fun getSince(member: JApiCompatibility): JavaSourceQuery<SinceTagStatus> =
        member.jApiClass.simpleName.let { declaringClassSimpleName ->
            JavaSourceQuery(
                SinceTagStatus.Missing,
                when (member) {
                    is JApiClass -> getSinceJavaClassVisitorFor(declaringClassSimpleName)
                    is JApiField -> getSinceJavaFieldVisitorFor(member)
                    is JApiConstructor -> getSinceJavaConstructorVisitorFor(declaringClassSimpleName)
                    is JApiMethod -> getSinceJavaMethodVisitorFor(declaringClassSimpleName, member)
                    else -> error("Unsupported japicmp member type ${member::class}")
                }
            )
        }
}


private
val MethodDeclaration.hasOverrideAnnotation: Boolean
    get() = annotations?.any { it.name.asString() == Override::class.simpleName } == true


private
fun getSinceJavaClassVisitorFor(classSimpleName: String) =
    object : SinceVisitor() {

        override fun visit(declaration: ClassOrInterfaceDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(classSimpleName) ?: super.visit(declaration, arg)

        override fun visit(declaration: AnnotationDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(classSimpleName) ?: super.visit(declaration, arg)

        override fun visit(declaration: EnumDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(classSimpleName) ?: super.visit(declaration, arg)
    }


private
fun getSinceJavaFieldVisitorFor(field: JApiField) =
    object : SinceVisitor() {

        override fun visit(declaration: FieldDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.takeIf { matchesName(it.fieldName, field.name) }?.getSince()?.let { SinceTagStatus.Present(it) }

        override fun visit(declaration: EnumConstantDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(field.name)
    }


private
fun getSinceJavaConstructorVisitorFor(classSimpleName: String) =
    object : SinceVisitor() {

        override fun visit(declaration: ConstructorDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(classSimpleName) ?: super.visit(declaration, arg)
    }


private
fun getSinceJavaMethodVisitorFor(classSimpleName: String, method: JApiMethod) =
    object : SinceVisitor() {

        override fun visit(declaration: AnnotationMemberDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(method.name) ?: super.visit(declaration, arg)

        override fun visit(declaration: MethodDeclaration, arg: Unit?): SinceTagStatus? =
            declaration.getSinceIfMatchesName(method.name)

        override fun visit(declaration: EnumDeclaration, arg: Unit?): SinceTagStatus? {
            return if (declaration.matchesName(classSimpleName) && method.isEnumImplicitMethod()) {
                SinceTagStatus.NotNeeded
            } else super.visit(declaration, arg)
        }
    }


private
fun <T> T.getSinceIfMatchesName(candidateName: String): SinceTagStatus? where T : BodyDeclaration<*>, T : NodeWithSimpleName<*> =
    takeIf { it.matchesName(candidateName) }?.getSince()?.let { SinceTagStatus.Present(it) }


private
fun <T : NodeWithSimpleName<*>> T.matchesName(candidateName: String) =
    matchesName(name.asString(), candidateName)


private
fun matchesName(name: String, candidateName: String) =
    name == candidateName.replace(".*\\$".toRegex(), "") // strip outer class names

private
val SINCE_REGEX = Regex("""@since ([^\s]+)""")

private
fun BodyDeclaration<*>.getSince(): String? =
    comment?.orElse(null)?.content?.let { content ->
        SINCE_REGEX.find(content)?.groupValues?.get(1)
    }


private
val FieldDeclaration.fieldName: String
    get() = variables.first().name.asString()


private
fun JApiMethod.isEnumImplicitMethod(): Boolean =
    isEnumImplicitMethodValues() ||
        isEnumImplicitMethodValueOf()


private
fun JApiMethod.isEnumImplicitMethodValueOf(): Boolean {
    return name == "valueOf" && parameters.size == 1 && parameters.first().type == "java.lang.String"
}


private
fun JApiMethod.isEnumImplicitMethodValues(): Boolean {
    return name == "values" && parameters.size == 0
}
