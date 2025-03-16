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
package gradlebuild.docs.dsl.source;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import gradlebuild.docs.dsl.source.model.AbstractLanguageElement;
import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.dsl.source.model.ClassMetaData.MetaType;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.PropertyMetaData;
import gradlebuild.docs.dsl.source.model.TypeMetaData;
import gradlebuild.docs.model.ClassMetaDataRepository;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceMetaDataVisitor extends VoidVisitorAdapter<ClassMetaDataRepository<ClassMetaData>> {
    private static final Pattern GETTER_METHOD_NAME = Pattern.compile("(get|is)(.+)");
    private static final Pattern SETTER_METHOD_NAME = Pattern.compile("set(.+)");
    private final List<ClassMetaData> allClasses = new ArrayList<ClassMetaData>();
    private final Deque<ClassMetaData> classStack = new LinkedList<ClassMetaData>();
    private String packageName;

    @Override
    public void visit(CompilationUnit compilationUnit, ClassMetaDataRepository<ClassMetaData> repository) {
        super.visit(compilationUnit, repository);
        compilationUnit.getImports().stream()
            .filter(anImport -> !anImport.isStatic())
            .map(anImport -> anImport.getNameAsString() + (anImport.isAsterisk() ? ".*" : ""))
            .forEach(anImport -> allClasses.forEach(classMetaData -> classMetaData.addImport(anImport)));
    }

    @Override
    public void visit(PackageDeclaration packageDeclaration, ClassMetaDataRepository<ClassMetaData> repository) {
        this.packageName = packageDeclaration.getNameAsString();
        super.visit(packageDeclaration, repository);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration classDeclaration, ClassMetaDataRepository<ClassMetaData> repository) {
        visitTypeDeclaration(classDeclaration, repository, classDeclaration.isInterface() ? MetaType.INTERFACE : MetaType.CLASS, () -> {
            visitExtendedTypes(classDeclaration);
            visitImplementedTypes(classDeclaration);
            super.visit(classDeclaration, repository);
        });
    }

    @Override
    public void visit(EnumDeclaration enumDeclaration, ClassMetaDataRepository<ClassMetaData> repository) {
        visitTypeDeclaration(enumDeclaration, repository, MetaType.ENUM, () -> {
            visitImplementedTypes(enumDeclaration);
            enumDeclaration.getEntries().forEach(entry -> getCurrentClass().addEnumConstant(entry.getNameAsString()));
            super.visit(enumDeclaration, repository);
        });
    }

    @Override
    public void visit(AnnotationDeclaration annotationDeclaration, ClassMetaDataRepository<ClassMetaData> repository) {
        visitTypeDeclaration(annotationDeclaration, repository, MetaType.ANNOTATION, () -> {
            super.visit(annotationDeclaration, repository);
        });
    }

    private void visitTypeDeclaration(TypeDeclaration<?> typeDeclaration, ClassMetaDataRepository<ClassMetaData> repository, ClassMetaData.MetaType metaType, Runnable action) {
        if (!typeDeclaration.isPublic()) {
            return;
        }
        ClassMetaData outerClass = classStack.isEmpty() ? null : getCurrentClass();
        String baseName = typeDeclaration.getNameAsString();
        String className = outerClass == null ? packageName + '.' + baseName : outerClass.getClassName() + '.' + baseName;
        String comment = getJavadocComment(typeDeclaration);
        ClassMetaData currentClass = new ClassMetaData(className, packageName, metaType, false, comment);
        if (outerClass != null) {
            outerClass.addInnerClassName(className);
            currentClass.setOuterClassName(outerClass.getClassName());
        }
        findAnnotations(typeDeclaration, currentClass);

        allClasses.add(currentClass);
        repository.put(className, currentClass);

        classStack.push(currentClass);
        action.run();
        classStack.pop();
    }

    private ClassMetaData getCurrentClass() {
        return classStack.peek();
    }

    private void visitExtendedTypes(NodeWithExtends<?> node) {
        ClassMetaData currentClass = getCurrentClass();
        for (ClassOrInterfaceType extendedType : node.getExtendedTypes()) {
            if (currentClass.isInterface()) {
                currentClass.addInterfaceName(extractName(extendedType));
            } else {
                currentClass.setSuperClassName(extractName(extendedType));
            }
        }
    }

    private void visitImplementedTypes(NodeWithImplements<?> node) {
        ClassMetaData currentClass = getCurrentClass();
        for (ClassOrInterfaceType implementedType : node.getImplementedTypes()) {
            currentClass.addInterfaceName(extractName(implementedType));
        }
    }

    @Override
    public void visit(MethodDeclaration methodDeclaration, ClassMetaDataRepository<ClassMetaData> repository) {
        String name = methodDeclaration.getNameAsString();

        String rawCommentText = getJavadocComment(methodDeclaration);
        TypeMetaData returnType = extractTypeName(methodDeclaration.getType());
        MethodMetaData methodMetaData = getCurrentClass().addMethod(name, returnType, rawCommentText);

        findAnnotations(methodDeclaration, methodMetaData);
        extractParameters(methodDeclaration, methodMetaData);

        Matcher matcher = GETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(2);
            String propName = name.substring(startName, startName + 1).toLowerCase(Locale.ROOT) + name.substring(startName + 1);
            PropertyMetaData property = getCurrentClass().addReadableProperty(propName, returnType, rawCommentText, methodMetaData);
            methodMetaData.getAnnotationTypeNames().forEach(property::addAnnotationTypeName);
            property.setReplacement(methodMetaData.getReplacement());
            return;
        }

        if (methodMetaData.getParameters().size() != 1) {
            return;
        }
        matcher = SETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(1);
            String propName = name.substring(startName, startName + 1).toLowerCase(Locale.ROOT) + name.substring(startName + 1);
            TypeMetaData type = methodMetaData.getParameters().get(0).getType();
            getCurrentClass().addWriteableProperty(propName, type, rawCommentText, methodMetaData);
        }
    }

    private void extractParameters(MethodDeclaration n, MethodMetaData method) {
        for (Parameter parameter : n.getParameters()) {
            TypeMetaData typeMetaData = extractTypeName(parameter.getType());
            if (parameter.isVarArgs()) {
                typeMetaData.setVarargs();
            }
            method.addParameter(parameter.getNameAsString(), typeMetaData);
        }
    }

    @Override
    public void visit(FieldDeclaration fieldDeclaration, ClassMetaDataRepository<ClassMetaData> arg) {
        boolean isConst = getCurrentClass().isInterface() || (fieldDeclaration.isStatic() && fieldDeclaration.isFinal());
        if (isConst) {
            fieldDeclaration.getVariables().forEach(variableDeclarator -> {
                String constName = variableDeclarator.getNameAsString();
                String value = variableDeclarator.getInitializer()
                    .filter(Expression::isLiteralStringValueExpr)
                    .map(Expression::asLiteralStringValueExpr)
                    .map(LiteralStringValueExpr::getValue)
                    .orElse(null);
                getCurrentClass().getConstants().put(constName, value);
            });
        }
    }

    private TypeMetaData extractTypeName(Type type) {
        TypeMetaData typeMetaData = new TypeMetaData();
        type.ifArrayType(arrayType -> typeMetaData.setArrayDimensions(arrayType.getArrayLevel()));
        extractElementTypeName(type.getElementType(), typeMetaData);
        return typeMetaData;
    }

    private void extractElementTypeName(Type elementType, TypeMetaData typeMetaData) {
        elementType.ifVoidType(voidType -> typeMetaData.setName("void"));
        elementType.ifPrimitiveType(primitiveType -> typeMetaData.setName(primitiveType.asString()));
        elementType.ifWildcardType(wildcardType -> {
            if (!wildcardType.getExtendedType().isPresent() && !wildcardType.getSuperType().isPresent()) {
                typeMetaData.setWildcard();
            } else {
                wildcardType.getExtendedType().ifPresent(referenceType -> typeMetaData.setUpperBounds(extractTypeName(referenceType)));
                wildcardType.getSuperType().ifPresent(referenceType -> typeMetaData.setLowerBounds(extractTypeName(referenceType)));
            }
        });
        elementType.ifClassOrInterfaceType(classOrInterfaceType -> {
            typeMetaData.setName(extractName(classOrInterfaceType));
            classOrInterfaceType.getTypeArguments().ifPresent(typeArguments -> {
                typeArguments.forEach(arg -> typeMetaData.addTypeArg(extractTypeName(arg)));
            });
        });
    }

    private void findAnnotations(NodeWithAnnotations<?> node, AbstractLanguageElement currentElement) {
        for (AnnotationExpr child : node.getAnnotations()) {
            if (child instanceof SingleMemberAnnotationExpr && child.getNameAsString().endsWith("ReplacedBy")) {
                currentElement.setReplacement(((SingleMemberAnnotationExpr) child).getMemberValue().asLiteralStringValueExpr().getValue());
            }
            currentElement.addAnnotationTypeName(child.getNameAsString());
        }
    }

    private String extractName(ClassOrInterfaceType type) {
        if (type.getScope().isPresent()) {
            return extractName(type.getScope().get()) + "." + type.getNameAsString();
        }
        return type.getNameAsString();
    }

    private String getJavadocComment(NodeWithJavadoc<?> node) {
        return node.getJavadocComment().map(Comment::getContent).orElse("");
    }

}
