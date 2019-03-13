/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.binarycompatibility.rules;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.Map;
import java.util.Optional;

public class SinceAnnotationMissingRule extends AbstractGradleViolationRule {

    public SinceAnnotationMissingRule(Map<String, String> acceptedViolations) {
        super(acceptedViolations);
    }

    @Override
    public Violation maybeViolation(final JApiCompatibility member) {
        String className = null;
        GenericVisitorAdapter<Object, Void> visitor = null;

        if (member instanceof JApiMethod && !isOverride((JApiMethod) member)) {
            final JApiMethod method = (JApiMethod) member;
            if (isDeprecated(method)) {
                return null;
            }
            className = method.getjApiClass().getFullyQualifiedName();
            visitor = new GenericVisitorAdapter<Object, Void>() {
                @Override
                public Object visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(classDeclaration.getName().asString(), toSimpleName(method.getjApiClass().getFullyQualifiedName()), classDeclaration)) {
                        return new Object();
                    }
                    return super.visit(classDeclaration, arg);
                }

                @Override
                public Object visit(AnnotationDeclaration annotationDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(annotationDeclaration.getName().asString(), toSimpleName(method.getjApiClass().getFullyQualifiedName()), annotationDeclaration)) {
                        return new Object();
                    }
                    return super.visit(annotationDeclaration, arg);
                }

                @Override
                public Object visit(EnumDeclaration enumDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumDeclaration.getName().asString(), toSimpleName(method.getjApiClass().getFullyQualifiedName()), enumDeclaration)) {
                        return new Object();
                    }
                    return super.visit(enumDeclaration, arg);
                }

                @Override
                public Object visit(MethodDeclaration methodDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(methodDeclaration.getName().asString(), method.getName(), methodDeclaration)) {
                        return new Object();
                    }
                    return null;
                }
            };
        } else if (member instanceof JApiField) {
            final JApiField field = (JApiField) member;
            if (isDeprecated(field)) {
                return null;
            }
            className = field.getjApiClass().getFullyQualifiedName();
            visitor = new GenericVisitorAdapter<Object, Void>() {
                @Override
                public Object visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(classDeclaration.getName().asString(), toSimpleName(field.getjApiClass().getFullyQualifiedName()), classDeclaration)) {
                        return new Object();
                    }
                    return super.visit(classDeclaration, arg);
                }

                @Override
                public Object visit(AnnotationDeclaration annotationDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(annotationDeclaration.getName().asString(), toSimpleName(field.getjApiClass().getFullyQualifiedName()), annotationDeclaration)) {
                        return new Object();
                    }
                    return super.visit(annotationDeclaration, arg);
                }

                @Override
                public Object visit(EnumDeclaration enumDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumDeclaration.getName().asString(), toSimpleName(field.getjApiClass().getFullyQualifiedName()), enumDeclaration)) {
                        return new Object();
                    }
                    return super.visit(enumDeclaration, arg);
                }

                @Override
                public Object visit(FieldDeclaration fieldDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(fieldDeclaration.getVariables().get(0).getNameAsString(), field.getName(), fieldDeclaration)) {
                        return new Object();
                    }
                    return null;
                }

                @Override
                public Object visit(EnumConstantDeclaration enumConstantDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumConstantDeclaration.getName().asString(), field.getName(), enumConstantDeclaration)) {
                        return new Object();
                    }
                    return null;
                }
            };
        } else if (member instanceof JApiConstructor) {
            final JApiConstructor constructor = (JApiConstructor) member;
            if (isDeprecated(constructor) || isInject(constructor)) {
                return null;
            }
            className = constructor.getjApiClass().getFullyQualifiedName();
            visitor = new GenericVisitorAdapter<Object, Void>() {
                @Override
                public Object visit(ConstructorDeclaration constructorDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(constructorDeclaration.getName().asString(), toSimpleName(constructor.getjApiClass().getFullyQualifiedName()), constructorDeclaration)) {
                        return new Object();
                    }
                    return super.visit(constructorDeclaration, arg);
                }

                @Override
                public Object visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(classDeclaration.getName().asString(), toSimpleName(constructor.getjApiClass().getFullyQualifiedName()), classDeclaration)) {
                        return new Object();
                    }
                    return super.visit(classDeclaration, arg);
                }

                @Override
                public Object visit(AnnotationDeclaration annotationDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(annotationDeclaration.getName().asString(), toSimpleName(constructor.getjApiClass().getFullyQualifiedName()), annotationDeclaration)) {
                        return new Object();
                    }
                    return super.visit(annotationDeclaration, arg);
                }

                @Override
                public Object visit(EnumDeclaration enumDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumDeclaration.getName().asString(), toSimpleName(constructor.getjApiClass().getFullyQualifiedName()), enumDeclaration)) {
                        return new Object();
                    }
                    return super.visit(enumDeclaration, arg);
                }

                @Override
                public Object visit(FieldDeclaration fieldDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(fieldDeclaration.getVariables().get(0).getNameAsString(), constructor.getName(), fieldDeclaration)) {
                        return new Object();
                    }
                    return null;
                }

                @Override
                public Object visit(EnumConstantDeclaration enumConstantDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumConstantDeclaration.getName().asString(), constructor.getName(), enumConstantDeclaration)) {
                        return new Object();
                    }
                    return null;
                }
            };
        } else if (member instanceof JApiClass) {
            final JApiClass clazz = (JApiClass) member;
            if (isDeprecated(clazz)) {
                return null;
            }
            className = clazz.getFullyQualifiedName();
            visitor = new GenericVisitorAdapter<Object, Void>() {
                @Override
                public Object visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(classDeclaration.getName().asString(), toSimpleName(clazz.getFullyQualifiedName()), classDeclaration)) {
                        return new Object();
                    }
                    return super.visit(classDeclaration, arg);
                }

                @Override
                public Object visit(AnnotationDeclaration annotationDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(annotationDeclaration.getName().asString(), toSimpleName(clazz.getFullyQualifiedName()), annotationDeclaration)) {
                        return new Object();
                    }
                    return super.visit(annotationDeclaration, arg);
                }

                @Override
                public Object visit(EnumDeclaration enumDeclaration, Void arg) {
                    if (matchesNameAndContainsAnnotation(enumDeclaration.getName().asString(), toSimpleName(clazz.getFullyQualifiedName()), enumDeclaration)) {
                        return new Object();
                    }
                    return super.visit(enumDeclaration, arg);
                }
            };
        }

        if (className == null) {
            return null;
        }

        // TODO:kotlin-dsl remove me
        if (isKotlinClass(jApiClass)) {
            return null;
        }

        try {
            Object result = JavaParser.parse(sourceFileFor(className)).accept(visitor, null);
            if (result == null) {
                return acceptOrReject(member, Violation.error(member, "Is not annotated with @since " + getCurrentVersion()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private String toSimpleName(String className) {
        String[] bits = className.split("\\.");
        return bits[bits.length - 1];
    }

    private boolean matchesNameAndContainsAnnotation(String name1, String name2, BodyDeclaration declaration) {
        Optional<Comment> comment = declaration.getComment();
        if (!comment.isPresent()) {
            return false;
        }
        name2 = name2.replaceAll(".*\\$", ""); //strip outer class names
        return name1.equals(name2) && comment.get().getContent().contains("@since " + getCurrentVersion());
    }

}
