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
package org.gradle.build.docs.dsl.source;

import groovyjarjarantlr.collections.AST;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.antlr.GroovySourceAST;
import org.codehaus.groovy.antlr.LineColumn;
import org.codehaus.groovy.antlr.SourceBuffer;
import org.codehaus.groovy.antlr.treewalker.VisitorAdapter;
import org.gradle.build.docs.dsl.source.model.*;
import org.gradle.build.docs.dsl.source.model.ClassMetaData.MetaType;
import org.gradle.build.docs.model.ClassMetaDataRepository;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.codehaus.groovy.antlr.parser.GroovyTokenTypes.*;

public class SourceMetaDataVisitor extends VisitorAdapter {
    private static final Pattern PREV_JAVADOC_COMMENT_PATTERN = Pattern.compile("(?s)/\\*\\*(.*?)\\*/");
    private static final Pattern GETTER_METHOD_NAME = Pattern.compile("(get|is)(.+)");
    private static final Pattern SETTER_METHOD_NAME = Pattern.compile("set(.+)");
    private final SourceBuffer sourceBuffer;
    private final LinkedList<GroovySourceAST> parseStack = new LinkedList<GroovySourceAST>();
    private final List<String> imports = new ArrayList<String>();
    private final ClassMetaDataRepository<ClassMetaData> repository;
    private final List<ClassMetaData> allClasses = new ArrayList<ClassMetaData>();
    private final LinkedList<ClassMetaData> classStack = new LinkedList<ClassMetaData>();
    private final Map<GroovySourceAST, ClassMetaData> typeTokens = new HashMap<GroovySourceAST, ClassMetaData>();
    private final boolean groovy;
    private String packageName;
    private LineColumn lastLineCol;

    SourceMetaDataVisitor(SourceBuffer sourceBuffer, ClassMetaDataRepository<ClassMetaData> repository,
                          boolean isGroovy) {
        this.sourceBuffer = sourceBuffer;
        this.repository = repository;
        groovy = isGroovy;
        lastLineCol = new LineColumn(1, 1);
    }

    public void complete() {
        for (String anImport : imports) {
            for (ClassMetaData classMetaData : allClasses) {
                classMetaData.addImport(anImport);
            }
        }
    }

    @Override
    public void visitPackageDef(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            packageName = extractName(t);
        }
    }

    @Override
    public void visitImport(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            imports.add(extractName(t));
        }
    }

    @Override
    public void visitClassDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, MetaType.CLASS);
    }

    @Override
    public void visitInterfaceDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, MetaType.INTERFACE);
    }

    @Override
    public void visitEnumDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, MetaType.ENUM);
    }

    @Override
    public void visitAnnotationDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, MetaType.ANNOTATION);
    }

    private void visitTypeDef(GroovySourceAST t, int visit, ClassMetaData.MetaType metaType) {
        if (visit == OPENING_VISIT) {
            ClassMetaData outerClass = getCurrentClass();
            String baseName = extractIdent(t);
            String className = outerClass != null ? outerClass.getClassName() + '.' + baseName
                    : packageName + '.' + baseName;
            String comment = getJavaDocCommentsBeforeNode(t);
            ClassMetaData currentClass = new ClassMetaData(className, packageName, metaType, groovy, comment);
            if (outerClass != null) {
                outerClass.addInnerClassName(className);
                currentClass.setOuterClassName(outerClass.getClassName());
            }
            findAnnotations(t, currentClass);
            classStack.addFirst(currentClass);
            allClasses.add(currentClass);
            typeTokens.put(t, currentClass);
            repository.put(className, currentClass);
        }
    }

    private ClassMetaData getCurrentClass() {
        return classStack.isEmpty() ? null : classStack.getFirst();
    }

    @Override
    public void visitExtendsClause(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            ClassMetaData currentClass = getCurrentClass();
            for (
                    GroovySourceAST child = (GroovySourceAST) t.getFirstChild(); child != null;
                    child = (GroovySourceAST) child.getNextSibling()) {
                if (!currentClass.isInterface()) {
                    currentClass.setSuperClassName(extractName(child));
                } else {
                    currentClass.addInterfaceName(extractName(child));
                }
            }
        }
    }

    @Override
    public void visitImplementsClause(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            ClassMetaData currentClass = getCurrentClass();
            for (
                    GroovySourceAST child = (GroovySourceAST) t.getFirstChild(); child != null;
                    child = (GroovySourceAST) child.getNextSibling()) {
                currentClass.addInterfaceName(extractName(child));
            }
        }
    }

    @Override
    public void visitEnumConstantDef(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            String name = extractName(t);
            getCurrentClass().getDeclaredEnumConstants().add(name);
            skipJavaDocComment(t);
        }
    }

    @Override
    public void visitMethodDef(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            maybeAddMethod(t);
            skipJavaDocComment(t);
        }
    }

    private void maybeAddMethod(GroovySourceAST t) {
        String name = extractName(t);
        if (!groovy && name.equals(getCurrentClass().getSimpleName())) {
            // A constructor. The java grammar treats a constructor as a method, the groovy grammar does not.
            return;
        }

        ASTIterator children = new ASTIterator(t);
        if (groovy) {
            children.skip(TYPE_PARAMETERS);
            children.skip(MODIFIERS);
        } else {
            children.skip(MODIFIERS);
            children.skip(TYPE_PARAMETERS);
        }

        String rawCommentText = getJavaDocCommentsBeforeNode(t);
        TypeMetaData returnType = extractTypeName(children.current);
        MethodMetaData method = getCurrentClass().addMethod(name, returnType, rawCommentText);

        findAnnotations(t, method);
        extractParameters(t, method);

        Matcher matcher = GETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(2);
            String propName = name.substring(startName, startName + 1).toLowerCase() + name.substring(startName + 1);
            PropertyMetaData property = getCurrentClass().addReadableProperty(propName, returnType, rawCommentText, method);
            for (String annotation : method.getAnnotationTypeNames()) {
                property.addAnnotationTypeName(annotation);
            }
            return;
        }

        if (method.getParameters().size() != 1) {
            return;
        }
        matcher = SETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(1);
            String propName = name.substring(startName, startName + 1).toLowerCase() + name.substring(startName + 1);
            TypeMetaData type = method.getParameters().get(0).getType();
            getCurrentClass().addWriteableProperty(propName, type, rawCommentText, method);
        }
    }

    private void extractParameters(GroovySourceAST t, MethodMetaData method) {
        GroovySourceAST paramsAst = t.childOfType(PARAMETERS);
        for (
                GroovySourceAST child = (GroovySourceAST) paramsAst.getFirstChild(); child != null;
                child = (GroovySourceAST) child.getNextSibling()) {
            assert child.getType() == PARAMETER_DEF || child.getType() == VARIABLE_PARAMETER_DEF;
            TypeMetaData type = extractTypeName((GroovySourceAST) child.getFirstChild().getNextSibling());
            if (child.getType() == VARIABLE_PARAMETER_DEF) {
                type.setVarargs();
            }
            method.addParameter(extractIdent(child), type);
        }
    }

    @Override
    public void visitVariableDef(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            maybeAddPropertyFromField(t);
            skipJavaDocComment(t);
        }
    }

    private void maybeAddPropertyFromField(GroovySourceAST t) {
        GroovySourceAST parentNode = getParentNode();
        boolean isField = parentNode != null && parentNode.getType() == OBJBLOCK;
        if (!isField) {
            return;
        }

        int modifiers = extractModifiers(t);
        boolean isConst = getCurrentClass().isInterface() || (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        if (isConst) {
            visitConst(t);
            return;
        }

        boolean isProp = groovy && !Modifier.isStatic(modifiers) && !Modifier.isPublic(modifiers)
                && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
        if (!isProp) {
            return;
        }

        ASTIterator children = new ASTIterator(t);
        children.skip(MODIFIERS);

        String propertyName = extractIdent(t);
        TypeMetaData propertyType = extractTypeName(children.current);
        ClassMetaData currentClass = getCurrentClass();

        MethodMetaData getterMethod = currentClass.addMethod(String.format("get%s", StringUtils.capitalize(
                propertyName)), propertyType, "");
        PropertyMetaData property = currentClass.addReadableProperty(propertyName, propertyType, getJavaDocCommentsBeforeNode(t), getterMethod);
        findAnnotations(t, property);
        if (!Modifier.isFinal(modifiers)) {
            MethodMetaData setterMethod = currentClass.addMethod(String.format("set%s", StringUtils.capitalize(
                    propertyName)), TypeMetaData.VOID, "");
            setterMethod.addParameter(propertyName, propertyType);
            currentClass.addWriteableProperty(propertyName, propertyType, getJavaDocCommentsBeforeNode(t), setterMethod);
        }
    }

    private void visitConst(GroovySourceAST t) {
        String constName = extractIdent(t);
        GroovySourceAST assign = t.childOfType(ASSIGN);
        String value = null;
        if (assign != null) {
            value = extractLiteral(assign.getFirstChild());
        }
        getCurrentClass().getConstants().put(constName, value);
    }

    private String extractLiteral(AST ast) {
        switch (ast.getType()) {
            case EXPR:
                // The java grammar wraps initialisers in an EXPR token
                return extractLiteral(ast.getFirstChild());
            case NUM_INT:
            case NUM_LONG:
            case NUM_FLOAT:
            case NUM_DOUBLE:
            case NUM_BIG_INT:
            case NUM_BIG_DECIMAL:
            case STRING_LITERAL:
                return ast.getText();
        }
        return null;
    }

    public GroovySourceAST pop() {
        if (!parseStack.isEmpty()) {
            GroovySourceAST ast = parseStack.removeFirst();
            ClassMetaData classMetaData = typeTokens.remove(ast);
            if (classMetaData != null) {
                assert classMetaData == classStack.getFirst();
                classStack.removeFirst();
            }
            return ast;
        }
        return null;
    }

    @Override
    public void push(GroovySourceAST t) {
        parseStack.addFirst(t);
    }

    private GroovySourceAST getParentNode() {
        if (parseStack.size() > 1) {
            return parseStack.get(1);
        }
        return null;
    }

    private int extractModifiers(GroovySourceAST ast) {
        GroovySourceAST modifiers = ast.childOfType(MODIFIERS);
        if (modifiers == null) {
            return 0;
        }
        int modifierFlags = 0;
        for (
                GroovySourceAST child = (GroovySourceAST) modifiers.getFirstChild(); child != null;
                child = (GroovySourceAST) child.getNextSibling()) {
            switch (child.getType()) {
                case LITERAL_private:
                    modifierFlags |= Modifier.PRIVATE;
                    break;
                case LITERAL_protected:
                    modifierFlags |= Modifier.PROTECTED;
                    break;
                case LITERAL_public:
                    modifierFlags |= Modifier.PUBLIC;
                    break;
                case FINAL:
                    modifierFlags |= Modifier.FINAL;
                    break;
                case LITERAL_static:
                    modifierFlags |= Modifier.STATIC;
                    break;
            }
        }
        return modifierFlags;
    }

    private TypeMetaData extractTypeName(GroovySourceAST ast) {
        TypeMetaData type = new TypeMetaData();
        switch (ast.getType()) {
            case TYPE:
                GroovySourceAST typeName = (GroovySourceAST) ast.getFirstChild();
                extractTypeName(typeName, type);
                break;
            case WILDCARD_TYPE:
                // In the groovy grammar, the bounds are sibling of the ?, in the java grammar, they are the child
                GroovySourceAST bounds = (GroovySourceAST) (groovy ? ast.getNextSibling() : ast.getFirstChild());
                if (bounds == null) {
                    type.setWildcard();
                } else if (bounds.getType() == TYPE_UPPER_BOUNDS) {
                    type.setUpperBounds(extractTypeName((GroovySourceAST) bounds.getFirstChild()));
                } else if (bounds.getType() == TYPE_LOWER_BOUNDS) {
                    type.setLowerBounds(extractTypeName((GroovySourceAST) bounds.getFirstChild()));
                }
                break;
            case IDENT:
            case DOT:
                extractTypeName(ast, type);
                break;
            default:
                throw new RuntimeException(String.format("Unexpected token in type name: %s", ast));
        }

        return type;
    }

    private void extractTypeName(GroovySourceAST ast, TypeMetaData type) {
        if (ast == null) {
            type.setName("java.lang.Object");
            return;
        }
        switch (ast.getType()) {
            case LITERAL_boolean:
                type.setName("boolean");
                return;
            case LITERAL_byte:
                type.setName("byte");
                return;
            case LITERAL_char:
                type.setName("char");
                return;
            case LITERAL_double:
                type.setName("double");
                return;
            case LITERAL_float:
                type.setName("float");
                return;
            case LITERAL_int:
                type.setName("int");
                return;
            case LITERAL_long:
                type.setName("long");
                return;
            case LITERAL_void:
                type.setName("void");
                return;
            case ARRAY_DECLARATOR:
                extractTypeName((GroovySourceAST) ast.getFirstChild(), type);
                type.addArrayDimension();
                return;
        }

        type.setName(extractName(ast));
        GroovySourceAST typeArgs = ast.childOfType(TYPE_ARGUMENTS);
        if (typeArgs != null) {
            for (
                    GroovySourceAST child = (GroovySourceAST) typeArgs.getFirstChild(); child != null;
                    child = (GroovySourceAST) child.getNextSibling()) {
                assert child.getType() == TYPE_ARGUMENT;
                type.addTypeArg(extractTypeName((GroovySourceAST) child.getFirstChild()));
            }
        }
    }

    private void skipJavaDocComment(GroovySourceAST t) {
        lastLineCol = new LineColumn(t.getLine(), t.getColumn());
    }

    private String getJavaDocCommentsBeforeNode(GroovySourceAST t) {
        String result = "";
        LineColumn thisLineCol = new LineColumn(t.getLine(), t.getColumn());
        String text = sourceBuffer.getSnippet(lastLineCol, thisLineCol);
        if (text != null) {
            Matcher m = PREV_JAVADOC_COMMENT_PATTERN.matcher(text);
            if (m.find()) {
                result = m.group(1);
            }
        }
        lastLineCol = thisLineCol;
        return result;
    }

    private void findAnnotations(GroovySourceAST t, AbstractLanguageElement currentElement) {
        GroovySourceAST modifiers = t.childOfType(MODIFIERS);
        if (modifiers != null) {
            List<GroovySourceAST> children = modifiers.childrenOfType(ANNOTATION);
            for (GroovySourceAST child : children) {
                String identifier = extractIdent(child);
                currentElement.addAnnotationTypeName(identifier);
            }
        }
    }

    private String extractIdent(GroovySourceAST t) {
        return t.childOfType(IDENT).getText();
    }

    private String extractName(GroovySourceAST t) {
        if (t.getType() == DOT) {
            GroovySourceAST firstChild = (GroovySourceAST) t.getFirstChild();
            GroovySourceAST secondChild = (GroovySourceAST) firstChild.getNextSibling();
            return extractName(firstChild) + "." + extractName(secondChild);
        }
        if (t.getType() == IDENT) {
            return t.getText();
        }
        if (t.getType() == STAR) {
            return t.getText();
        }

        GroovySourceAST child = t.childOfType(DOT);
        if (child != null) {
            return extractName(child);
        }
        child = t.childOfType(IDENT);
        if (child != null) {
            return extractName(child);
        }

        throw new RuntimeException(String.format("Unexpected token in name: %s", t));
    }

    private static class ASTIterator {
        GroovySourceAST current;

        private ASTIterator(GroovySourceAST parent) {
            this.current = (GroovySourceAST) parent.getFirstChild();
        }

        void skip(int token) {
            if (current != null && current.getType() == token) {
                current = (GroovySourceAST) current.getNextSibling();
            }
        }
    }
}
