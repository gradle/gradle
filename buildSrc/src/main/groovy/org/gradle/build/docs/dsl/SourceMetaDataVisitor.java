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
package org.gradle.build.docs.dsl;

import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.antlr.GroovySourceAST;
import org.codehaus.groovy.antlr.LineColumn;
import org.codehaus.groovy.antlr.SourceBuffer;
import org.codehaus.groovy.antlr.treewalker.VisitorAdapter;
import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.dsl.model.MethodMetaData;
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

    SourceMetaDataVisitor(SourceBuffer sourceBuffer, ClassMetaDataRepository<ClassMetaData> repository, boolean isGroovy) {
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
        visitTypeDef(t, visit, false);
    }

    @Override
    public void visitInterfaceDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, true);
    }

    @Override
    public void visitEnumDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, false);
    }

    @Override
    public void visitAnnotationDef(GroovySourceAST t, int visit) {
        visitTypeDef(t, visit, false);
    }

    private void visitTypeDef(GroovySourceAST t, int visit, boolean isInterface) {
        if (visit == OPENING_VISIT) {
            ClassMetaData outerClass = getCurrentClass();
            String baseName = extractIdent(t);
            String className = outerClass != null ? outerClass.getClassName() + '.' + baseName : packageName + '.' + baseName;
            String comment = getJavaDocCommentsBeforeNode(t);
            ClassMetaData currentClass = new ClassMetaData(className, packageName, isInterface, groovy, comment);
            if (outerClass != null) {
                outerClass.addInnerClassName(className);
                currentClass.setOuterClassName(outerClass.getClassName());
            }
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
            for (GroovySourceAST child = (GroovySourceAST) t.getFirstChild(); child != null; child = (GroovySourceAST) child.getNextSibling()) {
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
            for (GroovySourceAST child = (GroovySourceAST) t.getFirstChild(); child != null; child = (GroovySourceAST) child.getNextSibling()) {
                currentClass.addInterfaceName(extractName(child));
            }
        }
    }

    @Override
    public void visitMethodDef(GroovySourceAST t, int visit) {
        if (visit == OPENING_VISIT) {
            maybeAddPropertyFromMethod(t);
            skipJavaDocComment(t);
        }
    }

    private void maybeAddPropertyFromMethod(GroovySourceAST t) {
        String name = extractName(t);
        if (!groovy && name.equals(getCurrentClass().getSimpleName())) {
            // A constructor. The java grammar treats a constructor as a method, the groovy grammar does not.
            return;
        }

        String rawCommentText = getJavaDocCommentsBeforeNode(t);
        String returnType = extractTypeName(t);
        MethodMetaData method = getCurrentClass().addMethod(name, returnType, rawCommentText);

        extractParameters(t, method);

        Matcher matcher = GETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(2);
            String propName = name.substring(startName, startName + 1).toLowerCase() + name.substring(startName + 1);
            getCurrentClass().addReadableProperty(propName, returnType, rawCommentText);
            return;
        }

        if (method.getParameters().size() != 1) {
            return;
        }
        matcher = SETTER_METHOD_NAME.matcher(name);
        if (matcher.matches()) {
            int startName = matcher.start(1);
            String propName = name.substring(startName, startName + 1).toLowerCase() + name.substring(startName + 1);
            String type = method.getParameters().get(0).getType();
            getCurrentClass().addWriteableProperty(propName, type, rawCommentText);
        }
    }

    private void extractParameters(GroovySourceAST t, MethodMetaData method) {
        GroovySourceAST paramsAst = t.childOfType(PARAMETERS);
        for (GroovySourceAST child = (GroovySourceAST) paramsAst.getFirstChild(); child != null; child = (GroovySourceAST) child.getNextSibling()) {
            assert child.getType() == PARAMETER_DEF;
            method.addParameter(extractIdent(child), extractTypeName(child));
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
        boolean isProp = groovy && !Modifier.isStatic(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
        if (!isProp) {
            return;
        }

        String propertyName = extractIdent(t);
        String propertyType = extractTypeName(t);
        ClassMetaData currentClass = getCurrentClass();

        currentClass.addReadableProperty(propertyName, propertyType, getJavaDocCommentsBeforeNode(t));
        currentClass.addMethod(String.format("get%s", StringUtils.capitalize(propertyName)), propertyType, "");
        if (!Modifier.isFinal(modifiers)) {
            currentClass.addWriteableProperty(propertyName, propertyType, getJavaDocCommentsBeforeNode(t));
            MethodMetaData setterMethod = currentClass.addMethod(String.format("set%s", StringUtils.capitalize(
                    propertyName)), "void", "");
            setterMethod.addParameter(propertyName, propertyType);
        }
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
        for (GroovySourceAST child = (GroovySourceAST) modifiers.getFirstChild(); child != null; child = (GroovySourceAST) child.getNextSibling()) {
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

    private String extractTypeName(GroovySourceAST ast) {
        GroovySourceAST typeAst = ast.childOfType(TYPE);
        GroovySourceAST firstChild = (GroovySourceAST) typeAst.getFirstChild();
        if (firstChild == null) {
            return "java.lang.Object";
        }
        switch (firstChild.getType()) {
            case LITERAL_boolean:
                return "boolean";
            case LITERAL_byte:
                return "byte";
            case LITERAL_char:
                return "char";
            case LITERAL_double:
                return "double";
            case LITERAL_float:
                return "float";
            case LITERAL_int:
                return "int";
            case LITERAL_long:
                return "long";
            case LITERAL_void:
                return "void";
        }
        return extractName(firstChild);
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
}
