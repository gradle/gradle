/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CompileUnit;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.ResolveVisitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.trait.Traits;
import org.objectweb.asm.Opcodes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Gradle version of the Groovy {@link ResolveVisitor} that takes some shortcuts to make resolving faster. It tries to be as close as the original implementation, while having a significant impact
 * on performance, by taking advantage of knowledge of Gradle default imports, including a mapping from simple name to fully qualified class name. It also avoids unnecessary lookups of classnodes.
 *
 * @since 2.12
 */
public class GradleResolveVisitor extends ResolveVisitor {
    // note: BigInteger and BigDecimal are also imported by default
    private static final String[] DEFAULT_IMPORTS = {"java.lang.", "java.io.", "java.net.", "java.util.", "groovy.lang.", "groovy.util.", "java.time."};
    private static final String SCRIPTS_PACKAGE = "org.gradle.groovy.scripts";

    private ClassNode currentClass;
    private final Map<String, List<String>> simpleNameToFQN;
    private CompilationUnit compilationUnit;
    private SourceUnit source;
    private VariableScope currentScope;

    private boolean isTopLevelProperty = true;
    private boolean inPropertyExpression;
    private boolean inClosure;

    private Map<String, GenericsType> genericParameterNames = new HashMap<String, GenericsType>();
    private Set<FieldNode> fieldTypesChecked = new HashSet<FieldNode>();
    private boolean checkingVariableTypeInDeclaration;
    private ImportNode currImportNode;
    private MethodNode currentMethod;
    private ClassNodeResolver classNodeResolver;

    /**
     * A ConstructedNestedClass consists of an outer class and a name part, denoting a nested class with an unknown number of levels down. This allows resolve tests to skip this node for further inner
     * class searches and combinations with imports, since the outer class we know is already resolved.
     */
    private static class ConstructedNestedClass extends ClassNode {
        private final ClassNode knownEnclosingType;

        public ConstructedNestedClass(ClassNode outer, String inner) {
            super(outer.getName() + "$" + replacePoints(inner), Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            this.knownEnclosingType = outer;
            this.isPrimaryNode = false;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return knownEnclosingType.hasPackageName();
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("ConstructedNestedClass#setName should not be called");
            }
        }
    }

    private static String replacePoints(String name) {
        return name.replace('.', '$');
    }

    /**
     * we use ConstructedClassWithPackage to limit the resolving the compiler does when combining package names and class names. The idea that if we use a package, then we do not want to replace the
     * '.' with a '$' for the package part, only for the class name part. There is also the case of a imported class, so this logic can't be done in these cases...
     */
    private static class ConstructedClassWithPackage extends ClassNode {
        private final String prefix;
        private String className;

        public ConstructedClassWithPackage(String pkg, String name) {
            super(pkg + name, Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            isPrimaryNode = false;
            this.prefix = pkg;
            this.className = name;
        }

        @Override
        public String getName() {
            if (redirect() != this) {
                return super.getName();
            }
            return prefix + className;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return getName().indexOf('.') != -1;
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("ConstructedClassWithPackage#setName should not be called");
            }
        }
    }

    /**
     * we use LowerCaseClass to limit the resolving the compiler does for vanilla names starting with a lower case letter. The idea that if we use a vanilla name with a lower case letter, that this is
     * in most cases no class. If it is a class the class needs to be imported explicitly. The effect is that in an expression like "def foo = bar" we do not have to use a loadClass call to check the
     * name foo and bar for being classes. Instead we will ask the module for an alias for this name which is much faster.
     */
    private static class LowerCaseClass extends ClassNode {
        private final String className;

        public LowerCaseClass(String name) {
            super(name, Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            isPrimaryNode = false;
            this.className = name;
        }

        @Override
        public String getName() {
            if (redirect() != this) {
                return super.getName();
            }
            return className;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return false;
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("LowerCaseClass#setName should not be called");
            }
        }
    }

    public GradleResolveVisitor(CompilationUnit cu, Map<String, List<String>> simpleNameToFQN) {
        super(cu);
        compilationUnit = cu;
        this.classNodeResolver = new ClassNodeResolver();
        this.simpleNameToFQN = simpleNameToFQN;
    }

    @Override
    public void startResolving(ClassNode node, SourceUnit source) {
        this.source = source;
        visitClass(node);
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        VariableScope oldScope = currentScope;
        currentScope = node.getVariableScope();
        Map<String, GenericsType> oldPNames = genericParameterNames;
        genericParameterNames = new HashMap<String, GenericsType>(genericParameterNames);

        resolveGenericsHeader(node.getGenericsTypes());

        Parameter[] paras = node.getParameters();
        for (Parameter p : paras) {
            p.setInitialExpression(transform(p.getInitialExpression()));
            resolveOrFail(p.getType(), p.getType());
            visitAnnotations(p);
        }
        ClassNode[] exceptions = node.getExceptions();
        for (ClassNode t : exceptions) {
            resolveOrFail(t, node);
        }
        resolveOrFail(node.getReturnType(), node);

        MethodNode oldCurrentMethod = currentMethod;
        currentMethod = node;
        super.visitConstructorOrMethod(node, isConstructor);
        currentMethod = oldCurrentMethod;

        genericParameterNames = oldPNames;
        currentScope = oldScope;
    }

    @Override
    public void visitField(FieldNode node) {
        ClassNode t = node.getType();
        if (!fieldTypesChecked.contains(node)) {
            resolveOrFail(t, node);
        }
        super.visitField(node);
    }

    @Override
    public void visitProperty(PropertyNode node) {
        ClassNode t = node.getType();
        resolveOrFail(t, node);
        super.visitProperty(node);
        fieldTypesChecked.add(node.getField());
    }

    private boolean resolveToInner(ClassNode type) {
        // we do not do our name mangling to find an inner class
        // if the type is a ConstructedClassWithPackage, because in this case we
        // are resolving the name at a different place already
        if (type instanceof ConstructedClassWithPackage) {
            return false;
        }
        if (type instanceof ConstructedNestedClass) {
            return false;
        }
        String name = type.getName();
        String saved = name;
        while (true) {
            int len = name.lastIndexOf('.');
            if (len == -1) {
                break;
            }
            name = name.substring(0, len) + "$" + name.substring(len + 1);
            type.setName(name);
            if (resolve(type)) {
                return true;
            }
        }
        if (resolveToNestedOfCurrent(type)) {
            return true;
        }

        type.setName(saved);
        return false;
    }

    private boolean resolveToNestedOfCurrent(ClassNode type) {
        if (type instanceof ConstructedNestedClass) {
            return false;
        }
        // GROOVY-3110: It may be an inner enum defined by this class itself, in which case it does not need to be
        // explicitly qualified by the currentClass name
        String name = type.getName();
        if (currentClass != type && !name.contains(".") && type.getClass().equals(ClassNode.class)) {
            ClassNode tmp = new ConstructedNestedClass(currentClass, name);
            if (resolve(tmp)) {
                type.setRedirect(tmp);
                return true;
            }
        }
        return false;
    }

    private void resolveOrFail(ClassNode type, String msg, ASTNode node) {
        if (resolve(type)) {
            return;
        }
        if (resolveToInner(type)) {
            return;
        }
        addError("unable to resolve class " + type.getName() + " " + msg, node);
    }

    private void resolveOrFail(ClassNode type, ASTNode node, boolean preferImports) {
        resolveGenericsTypes(type.getGenericsTypes());
        if (preferImports && resolveAliasFromModule(type)) {
            return;
        }
        resolveOrFail(type, node);
    }

    private void resolveOrFail(ClassNode type, ASTNode node) {
        resolveOrFail(type, "", node);
    }

    private boolean resolve(ClassNode type) {
        return resolve(type, true, true, true);
    }

    private boolean resolve(ClassNode type, boolean testModuleImports, boolean testDefaultImports, boolean testStaticInnerClasses) {
        resolveGenericsTypes(type.getGenericsTypes());
        if (type.isResolved() || type.isPrimaryClassNode()) {
            return true;
        }
        if (type.isArray()) {
            ClassNode element = type.getComponentType();
            boolean resolved = resolve(element, testModuleImports, testDefaultImports, testStaticInnerClasses);
            if (resolved) {
                ClassNode cn = element.makeArray();
                type.setRedirect(cn);
            }
            return resolved;
        }

        // test if vanilla name is current class name
        if (currentClass == type) {
            return true;
        }

        String name = type.getName();
        if (genericParameterNames.get(name) != null) {
            GenericsType gt = genericParameterNames.get(name);
            type.setRedirect(gt.getType());
            type.setGenericsTypes(new GenericsType[]{gt});
            type.setGenericsPlaceHolder(true);
            return true;
        }

        if (currentClass.getNameWithoutPackage().equals(name)) {
            type.setRedirect(currentClass);
            return true;
        }

        if (type.hasPackageName()) {
            if (resolveFromResolver(type, name)) {
                return true;
            }
        }

        return resolveNestedClass(type)
            || resolveFromModule(type, testModuleImports)
            || resolveFromCompileUnit(type)
            || resolveFromDefaultImports(type, testDefaultImports)
            || resolveFromStaticInnerClasses(type, testStaticInnerClasses)
            || resolveToOuter(type);
    }

    private boolean resolveFromResolver(ClassNode type, String name) {
        ClassNodeResolver.LookupResult result = classNodeResolver.resolveName(name, compilationUnit);
        if (result != null && result.isClassNode()) {
            type.setRedirect(result.getClassNode());
            return true;
        }
        return false;
    }

    private boolean resolveNestedClass(ClassNode type) {
        if (type instanceof ConstructedNestedClass) {
            return false;
        }
        // we have for example a class name A, are in class X
        // and there is a nested class A$X. we want to be able
        // to access that class directly, so A becomes a valid
        // name in X.
        // GROOVY-4043: Do this check up the hierarchy, if needed
        Map<String, ClassNode> classHierarchy = new LinkedHashMap<String, ClassNode>();
        ClassNode val;
        for (ClassNode classToCheck = currentClass;
            /*
             * We know that DefaultScript & friends don't have user-visible nested types,
             * so don't try to look up nonsensical types like org.gradle.Script#sourceCompatibility
             */
             classToCheck != null && classToCheck != ClassHelper.OBJECT_TYPE && !SCRIPTS_PACKAGE.equals(classToCheck.getPackageName());
             classToCheck = classToCheck.getSuperClass()) {
            if (classHierarchy.containsKey(classToCheck.getName())) {
                break;
            }
            classHierarchy.put(classToCheck.getName(), classToCheck);
        }

        for (ClassNode classToCheck : classHierarchy.values()) {
            val = new ConstructedNestedClass(classToCheck, type.getName());
            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
                return true;
            }
            // also check interfaces in case we have interfaces with nested classes
            for (ClassNode next : classToCheck.getAllInterfaces()) {
                if (type.getName().contains(next.getName())) {
                    continue;
                }
                val = new ConstructedNestedClass(next, type.getName());
                if (resolve(val, false, false, false)) {
                    type.setRedirect(val);
                    return true;
                }
            }
        }

        // another case we want to check here is if we are in a
        // nested class A$B$C and want to access B without
        // qualifying it by A.B. A alone will work, since that
        // is the qualified (minus package) name of that class
        // anyway.

        // That means if the current class is not an InnerClassNode
        // there is nothing to be done.
        if (!(currentClass instanceof InnerClassNode)) {
            return false;
        }

        // since we have B and want to get A we start with the most
        // outer class, put them together and then see if that does
        // already exist. In case of B from within A$B we are done
        // after the first step already. In case of for example
        // A.B.C.D.E.F and accessing E from F we test A$E=failed,
        // A$B$E=failed, A$B$C$E=fail, A$B$C$D$E=success

        LinkedList<ClassNode> outerClasses = new LinkedList<ClassNode>();
        ClassNode outer = currentClass.getOuterClass();
        while (outer != null) {
            outerClasses.addFirst(outer);
            outer = outer.getOuterClass();
        }
        // most outer class is now element 0
        for (ClassNode testNode : outerClasses) {
            val = new ConstructedNestedClass(testNode, type.getName());
            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
                return true;
            }
            // also check interfaces in case we have interfaces with nested classes
            for (ClassNode next : testNode.getAllInterfaces()) {
                if (type.getName().contains(next.getName())) {
                    continue;
                }
                val = new ConstructedNestedClass(next, type.getName());
                if (resolve(val, false, false, false)) {
                    type.setRedirect(val);
                    return true;
                }
            }
        }

        return false;
    }

    private static String replaceLastPoint(String name) {
        int lastPoint = name.lastIndexOf('.');
        if (lastPoint>0) {
            name = name.substring(0, lastPoint)
                + "$"
                + name.substring(lastPoint + 1);
        }
        return name;
    }

    private boolean resolveFromStaticInnerClasses(ClassNode type, boolean testStaticInnerClasses) {
        if (type instanceof ConstructedNestedClass) {
            return false;
        }

        // a class consisting of a vanilla name can never be
        // a static inner class, because at least one dot is
        // required for this. Example: foo.bar -> foo$bar
        if (type instanceof LowerCaseClass) {
            return false;
        }

        // try to resolve a public static inner class' name
        testStaticInnerClasses &= type.hasPackageName();
        if (testStaticInnerClasses) {
            if (type instanceof ConstructedClassWithPackage) {
                // we replace '.' only in the className part
                // with '$' to find an inner class. The case that
                // the package is really a class is handled elsewhere
                ConstructedClassWithPackage tmp = (ConstructedClassWithPackage) type;
                String savedName = tmp.className;
                tmp.className = replaceLastPoint(savedName);
                if (resolve(tmp, false, true, false)) {
                    type.setRedirect(tmp.redirect());
                    return true;
                }
                tmp.className = savedName;
            } else {
                String savedName = type.getName();
                String replacedPointType = replaceLastPoint(savedName);
                type.setName(replacedPointType);
                if (resolve(type, false, true, false)) {
                    return true;
                }
                type.setName(savedName);
            }
        }
        return false;
    }

    private boolean resolveFromDefaultImports(ClassNode type, boolean testDefaultImports) {
        // test default imports
        testDefaultImports &= !type.hasPackageName();
        // we do not resolve a vanilla name starting with a lower case letter
        // try to resolve against a default import, because we know that the
        // default packages do not contain classes like these
        testDefaultImports &= !(type instanceof LowerCaseClass);
        if (testDefaultImports) {
            String name = type.getName();
            List<String> fqn = simpleNameToFQN.get(type.getName());
            if (fqn != null) {
                if (resolveFromResolver(type, fqn.get(0))) {
                    return true;
                }
            }
            for (String packagePrefix : DEFAULT_IMPORTS) {
                // We limit the inner class lookups here by using ConstructedClassWithPackage.
                // This way only the name will change, the packagePrefix will
                // not be included in the lookup. The case where the
                // packagePrefix is really a class is handled elsewhere.
                // WARNING: This code does not expect a class that has a static
                //          inner class in DEFAULT_IMPORTS
                ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(packagePrefix, name);
                if (resolve(tmp, false, false, false)) {
                    type.setRedirect(tmp.redirect());
                    return true;
                }
            }
            if (name.equals("BigInteger")) {
                type.setRedirect(ClassHelper.BigInteger_TYPE);
                return true;
            } else if (name.equals("BigDecimal")) {
                type.setRedirect(ClassHelper.BigDecimal_TYPE);
                return true;
            }
        }
        return false;
    }

    private boolean resolveFromCompileUnit(ClassNode type) {
        // look into the compile unit if there is a class with that name
        CompileUnit compileUnit = currentClass.getCompileUnit();
        if (compileUnit == null) {
            return false;
        }
        ClassNode cuClass = compileUnit.getClass(type.getName());
        if (cuClass != null) {
            if (type != cuClass) {
                type.setRedirect(cuClass);
            }
            return true;
        }
        return false;
    }

    private void ambiguousClass(ClassNode type, ClassNode iType, String name) {
        if (type.getName().equals(iType.getName())) {
            addError("reference to " + name + " is ambiguous, both class " + type.getName() + " and " + iType.getName() + " match", type);
        } else {
            type.setRedirect(iType);
        }
    }

    private boolean resolveAliasFromModule(ClassNode type) {
        // In case of getting a ConstructedClassWithPackage here we do not do checks for partial
        // matches with imported classes. The ConstructedClassWithPackage is already a constructed
        // node and any subclass resolving will then take place elsewhere
        if (type instanceof ConstructedClassWithPackage) {
            return false;
        }

        ModuleNode module = currentClass.getModule();
        if (module == null) {
            return false;
        }
        String name = type.getName();

        // check module node imports aliases
        // the while loop enables a check for inner classes which are not fully imported,
        // but visible as the surrounding class is imported and the inner class is public/protected static
        String pname = name;
        int index = name.length();
    /*
     * we have a name foo.bar and an import foo.foo. This means foo.bar is possibly
     * foo.foo.bar rather than foo.bar. This means to cut at the dot in foo.bar and
     * foo for import
     */
        while (true) {
            pname = name.substring(0, index);
            ClassNode aliasedNode = null;
            ImportNode importNode = module.getImport(pname);
            if (importNode != null && importNode != currImportNode) {
                aliasedNode = importNode.getType();
            }
            if (aliasedNode == null) {
                importNode = module.getStaticImports().get(pname);
                if (importNode != null && importNode != currImportNode) {
                    // static alias only for inner classes and must be at end of chain
                    ClassNode tmp = new ConstructedNestedClass(importNode.getType(), importNode.getFieldName());
                    if (resolve(tmp, false, false, true)) {
                        if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                            type.setRedirect(tmp.redirect());
                            return true;
                        }
                    }
                }
            }

            if (aliasedNode != null) {
                if (pname.length() == name.length()) {
                    // full match

                    // We can compare here by length, because pname is always
                    // a substring of name, so same length means they are equal.
                    type.setRedirect(aliasedNode);
                    return true;
                } else {
                    //partial match

                    // At this point we know that we have a match for pname. This may
                    // mean, that name[pname.length()..<-1] is a static inner class.
                    // For this the rest of the name does not need any dots in its name.
                    // It is either completely a inner static class or it is not.
                    // Since we do not want to have useless lookups we create the name
                    // completely and use a ConstructedClassWithPackage to prevent lookups against the package.
                    String className = aliasedNode.getNameWithoutPackage() + '$'
                        + name.substring(pname.length() + 1).replace('.', '$');
                    ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(aliasedNode.getPackageName() + ".", className);
                    if (resolve(tmp, true, true, false)) {
                        type.setRedirect(tmp.redirect());
                        return true;
                    }
                }
            }
            index = pname.lastIndexOf('.');
            if (index == -1) {
                break;
            }
        }
        return false;
    }

    private boolean resolveFromModule(ClassNode type, boolean testModuleImports) {
        if (type instanceof ConstructedNestedClass) {
            return false;
        }

        // we decided if we have a vanilla name starting with a lower case
        // letter that we will not try to resolve this name against .*
        // imports. Instead a full import is needed for these.
        // resolveAliasFromModule will do this check for us. This method
        // does also check the module contains a class in the same package
        // of this name. This check is not done for vanilla names starting
        // with a lower case letter anymore
        if (type instanceof LowerCaseClass) {
            return resolveAliasFromModule(type);
        }

        String name = type.getName();
        ModuleNode module = currentClass.getModule();
        if (module == null) {
            return false;
        }

        boolean newNameUsed = false;
        // we add a package if there is none yet and the module has one. But we
        // do not add that if the type is a ConstructedClassWithPackage. The code in ConstructedClassWithPackage
        // hasPackageName() will return true if ConstructedClassWithPackage#className has no dots.
        // but since the prefix may have them and the code there does ignore that
        // fact. We check here for ConstructedClassWithPackage.
        if (!type.hasPackageName() && module.hasPackageName() && !(type instanceof ConstructedClassWithPackage)) {
            type.setName(module.getPackageName() + name);
            newNameUsed = true;
        }
        // look into the module node if there is a class with that name
        List<ClassNode> moduleClasses = module.getClasses();
        for (ClassNode mClass : moduleClasses) {
            if (mClass.getName().equals(type.getName())) {
                if (mClass != type) {
                    type.setRedirect(mClass);
                }
                return true;
            }
        }
        if (newNameUsed) {
            type.setName(name);
        }

        if (testModuleImports) {
            if (resolveAliasFromModule(type)) {
                return true;
            }

            if (module.hasPackageName()) {
                // check package this class is defined in. The usage of ConstructedClassWithPackage here
                // means, that the module package will not be involved when the
                // compiler tries to find an inner class.
                ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(module.getPackageName(), name);
                if (resolve(tmp, false, false, false)) {
                    ambiguousClass(type, tmp, name);
                    type.setRedirect(tmp.redirect());
                    return true;
                }
            }

            if (!name.contains(".")) {
                // check module static imports (for static inner classes)
                for (ImportNode importNode : module.getStaticImports().values()) {
                    if (importNode.getFieldName().equals(name)) {
                        ClassNode tmp = new ConstructedNestedClass(importNode.getType(), name);
                        if (resolve(tmp, false, false, true)) {
                            if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                                type.setRedirect(tmp.redirect());
                                return true;
                            }
                        }
                    }
                }

                // check module node import packages
                for (ImportNode importNode : module.getStarImports()) {
                    String packagePrefix = importNode.getPackageName();
                    // We limit the inner class lookups here by using ConstructedClassWithPackage.
                    // This way only the name will change, the packagePrefix will
                    // not be included in the lookup. The case where the
                    // packagePrefix is really a class is handled elsewhere.
                    ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(packagePrefix, name);
                    if (resolve(tmp, false, false, true)) {
                        ambiguousClass(type, tmp, name);
                        type.setRedirect(tmp.redirect());
                        return true;
                    }
                }

                // check for star imports (import static pkg.Outer.*) matching static inner classes
                for (ImportNode importNode : module.getStaticStarImports().values()) {
                    ClassNode tmp = new ConstructedNestedClass(importNode.getType(), name);
                    if (resolve(tmp, false, false, true)) {
                        if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                            ambiguousClass(type, tmp, name);
                            type.setRedirect(tmp.redirect());
                            return true;
                        }
                    }

                }
            }
        }
        return false;
    }

    private final static ClassNode NO_CLASS;

    static {
        ClassNode cn = null;
        try {
            try {
                cn = (ClassNode) ClassNodeResolver.class.getDeclaredField("NO_CLASS").get(null);
            } catch (IllegalAccessException e) {
                cn = null;
            } catch (NoSuchFieldException e) {
                cn = null;
            }
        } finally {
            NO_CLASS = cn;
        }
    }

    private boolean resolveToOuter(ClassNode type) {
        String name = type.getName();

        // We do not need to check instances of LowerCaseClass
        // to be a Class, because unless there was an import for
        // for this we do not lookup these cases. This was a decision
        // made on the mailing list. To ensure we will not visit this
        // method again we set a NO_CLASS for this name
        if (type instanceof LowerCaseClass) {
            classNodeResolver.cacheClass(name, NO_CLASS);
            return false;
        }
        if (name.contains("$")) {
            return false;
        }

        if (currentClass.getModule().hasPackageName() && name.indexOf('.') == -1) {
            return false;
        }
        ClassNodeResolver.LookupResult lr = null;
        lr = classNodeResolver.resolveName(name, compilationUnit);
        if (lr != null) {
            if (lr.isSourceUnit()) {
                SourceUnit su = lr.getSourceUnit();
                currentClass.getCompileUnit().addClassNodeToCompile(type, su);
            } else {
                type.setRedirect(lr.getClassNode());
            }
            return true;
        }
        return false;
    }


    @Override
    public Expression transform(Expression exp) {
        if (exp == null) {
            return null;
        }
        Expression ret = null;
        if (exp instanceof VariableExpression) {
            ret = transformVariableExpression((VariableExpression) exp);
        } else if (exp.getClass() == PropertyExpression.class) {
            ret = transformPropertyExpression((PropertyExpression) exp);
        } else if (exp instanceof DeclarationExpression) {
            ret = transformDeclarationExpression((DeclarationExpression) exp);
        } else if (exp instanceof BinaryExpression) {
            ret = transformBinaryExpression((BinaryExpression) exp);
        } else if (exp instanceof MethodCallExpression) {
            ret = transformMethodCallExpression((MethodCallExpression) exp);
        } else if (exp instanceof ClosureExpression) {
            ret = transformClosureExpression((ClosureExpression) exp);
        } else if (exp instanceof ConstructorCallExpression) {
            ret = transformConstructorCallExpression((ConstructorCallExpression) exp);
        } else if (exp instanceof AnnotationConstantExpression) {
            ret = transformAnnotationConstantExpression((AnnotationConstantExpression) exp);
        } else {
            resolveOrFail(exp.getType(), exp);
            ret = exp.transformExpression(this);
        }
        if (ret != null && ret != exp) {
            ret.setSourcePosition(exp);
        }
        return ret;
    }

    private String lookupClassName(PropertyExpression pe) {
        boolean doInitialClassTest = true;
        String name = "";
        // this loop builds a name from right to left each name part
        // separated by "."
        for (Expression it = pe; it != null; it = ((PropertyExpression) it).getObjectExpression()) {
            if (it instanceof VariableExpression) {
                VariableExpression ve = (VariableExpression) it;
                // stop at super and this
                if (ve.isSuperExpression() || ve.isThisExpression()) {
                    return null;
                }
                String varName = ve.getName();
                if (doInitialClassTest) {
                    // we are at the first name part. This is the right most part.
                    // If this part is in lower case, then we do not need a class
                    // check. other parts of the property expression will be tested
                    // by a different method call to this method, so foo.Bar.bar
                    // can still be resolved to the class foo.Bar and the static
                    // field bar.
                    if (!testVanillaNameForClass(varName)) {
                        return null;
                    }
                    doInitialClassTest = false;
                    name = varName;
                } else {
                    name = varName + "." + name;
                }
                break;
            } else if (it.getClass() != PropertyExpression.class) {
                // anything other than PropertyExpressions or
                // VariableExpressions will stop resolving
                return null;
            } else {
                PropertyExpression current = (PropertyExpression) it;
                String propertyPart = current.getPropertyAsString();
                // the class property stops resolving, dynamic property names too
                if (propertyPart == null || propertyPart.equals("class")) {
                    return null;
                }
                if (doInitialClassTest) {
                    // we are at the first name part. This is the right most part.
                    // If this part is in lower case, then we do not need a class
                    // check. other parts of the property expression will be tested
                    // by a different method call to this method, so foo.Bar.bar
                    // can still be resolved to the class foo.Bar and the static
                    // field bar.
                    if (!testVanillaNameForClass(propertyPart)) {
                        return null;
                    }
                    doInitialClassTest = false;
                    name = propertyPart;
                } else {
                    name = propertyPart + "." + name;
                }
            }
        }
        if (name.length() == 0) {
            return null;
        }
        return name;
    }

    // iterate from the inner most to the outer and check for classes
    // this check will ignore a .class property, for Example Integer.class will be
    // a PropertyExpression with the ClassExpression of Integer as objectExpression
    // and class as property
    private Expression correctClassClassChain(PropertyExpression pe) {
        LinkedList<Expression> stack = new LinkedList<Expression>();
        ClassExpression found = null;
        for (Expression it = pe; it != null; it = ((PropertyExpression) it).getObjectExpression()) {
            if (it instanceof ClassExpression) {
                found = (ClassExpression) it;
                break;
            } else if (!(it.getClass() == PropertyExpression.class)) {
                return pe;
            }
            stack.addFirst(it);
        }
        if (found == null) {
            return pe;
        }

        if (stack.isEmpty()) {
            return pe;
        }
        Object stackElement = stack.removeFirst();
        if (!(stackElement.getClass() == PropertyExpression.class)) {
            return pe;
        }
        PropertyExpression classPropertyExpression = (PropertyExpression) stackElement;
        String propertyNamePart = classPropertyExpression.getPropertyAsString();
        if (propertyNamePart == null || !propertyNamePart.equals("class")) {
            return pe;
        }

        found.setSourcePosition(classPropertyExpression);
        if (stack.isEmpty()) {
            return found;
        }
        stackElement = stack.removeFirst();
        if (!(stackElement.getClass() == PropertyExpression.class)) {
            return pe;
        }
        PropertyExpression classPropertyExpressionContainer = (PropertyExpression) stackElement;

        classPropertyExpressionContainer.setObjectExpression(found);
        return pe;
    }

    @Override
    protected Expression transformPropertyExpression(PropertyExpression pe) {
        boolean itlp = isTopLevelProperty;
        boolean ipe = inPropertyExpression;

        Expression objectExpression = pe.getObjectExpression();
        inPropertyExpression = true;
        isTopLevelProperty = objectExpression.getClass() != PropertyExpression.class;
        objectExpression = transform(objectExpression);
        // we handle the property part as if it were not part of the property
        inPropertyExpression = false;
        Expression property = transform(pe.getProperty());
        isTopLevelProperty = itlp;
        inPropertyExpression = ipe;

        boolean spreadSafe = pe.isSpreadSafe();
        PropertyExpression old = pe;
        pe = new PropertyExpression(objectExpression, property, pe.isSafe());
        pe.setSpreadSafe(spreadSafe);
        pe.setSourcePosition(old);

        String className = lookupClassName(pe);
        if (className != null) {
            ClassNode type = ClassHelper.make(className);
            if (resolve(type)) {
                Expression ret = new ClassExpression(type);
                ret.setSourcePosition(pe);
                return ret;
            }
        }
        if (objectExpression instanceof ClassExpression && pe.getPropertyAsString() != null) {
            // possibly an inner class
            ClassExpression ce = (ClassExpression) objectExpression;
            ClassNode type = new ConstructedNestedClass(ce.getType(), pe.getPropertyAsString());
            if (resolve(type, false, false, false)) {
                Expression ret = new ClassExpression(type);
                ret.setSourcePosition(ce);
                return ret;
            }
        }
        Expression ret = pe;
        checkThisAndSuperAsPropertyAccess(pe);
        if (isTopLevelProperty) {
            ret = correctClassClassChain(pe);
        }
        return ret;
    }

    private boolean directlyImplementsTrait(ClassNode trait) {
        ClassNode[] interfaces = currentClass.getInterfaces();
        if (interfaces == null) {
            return currentClass.getSuperClass().equals(trait);
        }
        for (ClassNode node : interfaces) {
            if (node.equals(trait)) {
                return true;
            }
        }
        return currentClass.getSuperClass().equals(trait);
    }

    private void checkThisAndSuperAsPropertyAccess(PropertyExpression expression) {
        if (expression.isImplicitThis()) {
            return;
        }
        String prop = expression.getPropertyAsString();
        if (prop == null) {
            return;
        }
        if (!prop.equals("this") && !prop.equals("super")) {
            return;
        }

        ClassNode type = expression.getObjectExpression().getType();
        if (expression.getObjectExpression() instanceof ClassExpression) {
            if (!(currentClass instanceof InnerClassNode) && !Traits.isTrait(type)) {
                addError("The usage of 'Class.this' and 'Class.super' is only allowed in nested/inner classes.", expression);
                return;
            }
            if (currentScope != null && !currentScope.isInStaticContext() && Traits.isTrait(type) && "super".equals(prop) && directlyImplementsTrait(type)) {
                return;
            }
            ClassNode iterType = currentClass;
            while (iterType != null) {
                if (iterType.equals(type)) {
                    break;
                }
                iterType = iterType.getOuterClass();
            }
            if (iterType == null) {
                addError("The class '" + type.getName() + "' needs to be an outer class of '"
                    + currentClass.getName() + "' when using '.this' or '.super'.", expression);
            }
            if ((currentClass.getModifiers() & Opcodes.ACC_STATIC) == 0) {
                return;
            }
            if (currentScope != null && !currentScope.isInStaticContext()) {
                return;
            }
            addError("The usage of 'Class.this' and 'Class.super' within static nested class '"
                + currentClass.getName() + "' is not allowed in a static context.", expression);
        }
    }

    @Override
    protected Expression transformVariableExpression(VariableExpression ve) {
        visitAnnotations(ve);
        Variable v = ve.getAccessedVariable();

        if (!(v instanceof DynamicVariable) && !checkingVariableTypeInDeclaration) {
        /*
         *  GROOVY-4009: when a normal variable is simply being used, there is no need to try to
         *  resolve its type. Variable type resolve should proceed only if the variable is being declared.
         */
            return ve;
        }
        if (v instanceof DynamicVariable) {
            String name = ve.getName();
            ClassNode t = ClassHelper.make(name);
            // asking isResolved here allows to check if a primitive
            // type name like "int" was used to make t. In such a case
            // we have nothing left to do.
            boolean isClass = t.isResolved();
            if (!isClass) {
                // It was no primitive type, so next we see if the name,
                // which is a vanilla name, starts with a lower case letter.
                // In that case we change it to a LowerCaseClass to let the
                // compiler skip the resolving at several places in this class.
                if (Character.isLowerCase(name.charAt(0))) {
                    t = new LowerCaseClass(name);
                }
                isClass = resolve(t);
                if (!isClass) {
                    isClass = resolveToNestedOfCurrent(t);
                }
            }
            if (isClass) {
                // the name is a type so remove it from the scoping
                // as it is only a classvariable, it is only in
                // referencedClassVariables, but must be removed
                // for each parentscope too
                for (VariableScope scope = currentScope; scope != null && !scope.isRoot(); scope = scope.getParent()) {
                    if (scope.isRoot()) {
                        break;
                    }
                    if (scope.removeReferencedClassVariable(ve.getName()) == null) {
                        break;
                    }
                }
                ClassExpression ce = new ClassExpression(t);
                ce.setSourcePosition(ve);
                return ce;
            }
        }
        resolveOrFail(ve.getType(), ve);
        ClassNode origin = ve.getOriginType();
        if (origin != ve.getType()) {
            resolveOrFail(origin, ve);
        }
        return ve;
    }

    private boolean testVanillaNameForClass(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        return !Character.isLowerCase(name.charAt(0));
    }

    private boolean isLeftSquareBracket(int op) {
        return op == Types.ARRAY_EXPRESSION
            || op == Types.LEFT_SQUARE_BRACKET
            || op == Types.SYNTH_LIST
            || op == Types.SYNTH_MAP;
    }

    @Override
    protected Expression transformBinaryExpression(BinaryExpression be) {
        Expression left = transform(be.getLeftExpression());
        int type = be.getOperation().getType();
        if ((type == Types.ASSIGNMENT_OPERATOR || type == Types.EQUAL)
            && left instanceof ClassExpression) {
            ClassExpression ce = (ClassExpression) left;
            String error = "you tried to assign a value to the class '" + ce.getType().getName() + "'";
            if (ce.getType().isScript()) {
                error += ". Do you have a script with this name?";
            }
            addError(error, be.getLeftExpression());
            return be;
        }
        if (left instanceof ClassExpression && isLeftSquareBracket(type)) {
            if (be.getRightExpression() instanceof ListExpression) {
                ListExpression list = (ListExpression) be.getRightExpression();
                if (list.getExpressions().isEmpty()) {
                    // we have C[] if the list is empty -> should be an array then!
                    final ClassExpression ce = new ClassExpression(left.getType().makeArray());
                    ce.setSourcePosition(be);
                    return ce;
                } else {
                    // may be we have C[k1:v1, k2:v2] -> should become (C)([k1:v1, k2:v2])
                    boolean map = true;
                    for (Expression expression : list.getExpressions()) {
                        if (!(expression instanceof MapEntryExpression)) {
                            map = false;
                            break;
                        }
                    }

                    if (map) {
                        final MapExpression me = new MapExpression();
                        for (Expression expression : list.getExpressions()) {
                            me.addMapEntryExpression((MapEntryExpression) transform(expression));
                        }
                        me.setSourcePosition(list);
                        final CastExpression ce = new CastExpression(left.getType(), me);
                        ce.setSourcePosition(be);
                        return ce;
                    }
                }
            } else if (be.getRightExpression() instanceof SpreadMapExpression) {
                // we have C[*:map] -> should become (C) map
                SpreadMapExpression mapExpression = (SpreadMapExpression) be.getRightExpression();
                Expression right = transform(mapExpression.getExpression());
                Expression ce = new CastExpression(left.getType(), right);
                ce.setSourcePosition(be);
                return ce;
            }

            if (be.getRightExpression() instanceof MapEntryExpression) {
                // may be we have C[k1:v1] -> should become (C)([k1:v1])
                final MapExpression me = new MapExpression();
                me.addMapEntryExpression((MapEntryExpression) transform(be.getRightExpression()));
                me.setSourcePosition(be.getRightExpression());
                final CastExpression ce = new CastExpression(left.getType(), me);
                ce.setSourcePosition(be);
                return ce;
            }
        }
        Expression right = transform(be.getRightExpression());
        be.setLeftExpression(left);
        be.setRightExpression(right);
        return be;
    }

    @Override
    protected Expression transformClosureExpression(ClosureExpression ce) {
        boolean oldInClosure = inClosure;
        inClosure = true;
        Parameter[] paras = ce.getParameters();
        if (paras != null) {
            for (Parameter para : paras) {
                ClassNode t = para.getType();
                resolveOrFail(t, ce);
                visitAnnotations(para);
                if (para.hasInitialExpression()) {
                    Object initialVal = para.getInitialExpression();
                    if (initialVal instanceof Expression) {
                        para.setInitialExpression(transform((Expression) initialVal));
                    }
                }
                visitAnnotations(para);
            }
        }
        Statement code = ce.getCode();
        if (code != null) {
            code.visit(this);
        }
        inClosure = oldInClosure;
        return ce;
    }

    @Override
    protected Expression transformConstructorCallExpression(ConstructorCallExpression cce) {
        ClassNode type = cce.getType();
        resolveOrFail(type, cce);
        if (Modifier.isAbstract(type.getModifiers())) {
            addError("You cannot create an instance from the abstract " + getDescription(type) + ".", cce);
        }

        Expression ret = cce.transformExpression(this);
        return ret;
    }

    private String getDescription(ClassNode node) {
        return (node.isInterface() ? "interface" : "class") + " '" + node.getName() + "'";
    }

    @Override
    protected Expression transformMethodCallExpression(MethodCallExpression mce) {
        Expression args = transform(mce.getArguments());
        Expression method = transform(mce.getMethod());
        Expression object = transform(mce.getObjectExpression());

        resolveGenericsTypes(mce.getGenericsTypes());

        MethodCallExpression result = new MethodCallExpression(object, method, args);
        result.setSafe(mce.isSafe());
        result.setImplicitThis(mce.isImplicitThis());
        result.setSpreadSafe(mce.isSpreadSafe());
        result.setSourcePosition(mce);
        result.setGenericsTypes(mce.getGenericsTypes());
        result.setMethodTarget(mce.getMethodTarget());
        return result;
    }

    @Override
    protected Expression transformDeclarationExpression(DeclarationExpression de) {
        visitAnnotations(de);
        Expression oldLeft = de.getLeftExpression();
        checkingVariableTypeInDeclaration = true;
        Expression left = transform(oldLeft);
        checkingVariableTypeInDeclaration = false;
        if (left instanceof ClassExpression) {
            ClassExpression ce = (ClassExpression) left;
            addError("you tried to assign a value to the class " + ce.getType().getName(), oldLeft);
            return de;
        }
        Expression right = transform(de.getRightExpression());
        if (right == de.getRightExpression()) {
            fixDeclaringClass(de);
            return de;
        }
        DeclarationExpression newDeclExpr = new DeclarationExpression(left, de.getOperation(), right);
        newDeclExpr.setDeclaringClass(de.getDeclaringClass());
        fixDeclaringClass(newDeclExpr);
        newDeclExpr.setSourcePosition(de);
        newDeclExpr.addAnnotations(de.getAnnotations());
        return newDeclExpr;
    }

    // TODO get normal resolving to set declaring class
    private void fixDeclaringClass(DeclarationExpression newDeclExpr) {
        if (newDeclExpr.getDeclaringClass() == null && currentMethod != null) {
            newDeclExpr.setDeclaringClass(currentMethod.getDeclaringClass());
        }
    }

    @Override
    protected Expression transformAnnotationConstantExpression(AnnotationConstantExpression ace) {
        AnnotationNode an = (AnnotationNode) ace.getValue();
        ClassNode type = an.getClassNode();
        resolveOrFail(type, ", unable to find class for annotation", an);
        for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
            member.setValue(transform(member.getValue()));
        }
        return ace;
    }

    @Override
    public void visitAnnotations(AnnotatedNode node) {
        List<AnnotationNode> annotations = node.getAnnotations();
        if (annotations.isEmpty()) {
            return;
        }
        Map<String, AnnotationNode> tmpAnnotations = new HashMap<String, AnnotationNode>();
        ClassNode annType;
        for (AnnotationNode an : annotations) {
            // skip built-in properties
            if (an.isBuiltIn()) {
                continue;
            }
            annType = an.getClassNode();
            resolveOrFail(annType, ",  unable to find class for annotation", an);
            for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
                Expression newValue = transform(member.getValue());
                newValue = transformInlineConstants(newValue);
                member.setValue(newValue);
                checkAnnotationMemberValue(newValue);
            }
            if (annType.isResolved()) {
                Class annTypeClass = annType.getTypeClass();
                Retention retAnn = (Retention) annTypeClass.getAnnotation(Retention.class);
                if (retAnn != null && retAnn.value().equals(RetentionPolicy.RUNTIME)) {
                    AnnotationNode anyPrevAnnNode = tmpAnnotations.put(annTypeClass.getName(), an);
                    if (anyPrevAnnNode != null) {
                        addError("Cannot specify duplicate annotation on the same member : " + annType.getName(), an);
                    }
                }
            }
        }
    }

    // resolve constant-looking expressions statically (do here as gets transformed away later)
    private Expression transformInlineConstants(Expression exp) {
        if (exp instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) exp;
            if (pe.getObjectExpression() instanceof ClassExpression) {
                ClassExpression ce = (ClassExpression) pe.getObjectExpression();
                ClassNode type = ce.getType();
                if (type.isEnum()) {
                    return exp;
                }

                FieldNode fn = type.getField(pe.getPropertyAsString());
                if (fn != null && !fn.isEnum() && fn.isStatic() && fn.isFinal()) {
                    if (fn.getInitialValueExpression() instanceof ConstantExpression) {
                        return fn.getInitialValueExpression();
                    }
                }
            }
        } else if (exp instanceof ListExpression) {
            ListExpression le = (ListExpression) exp;
            ListExpression result = new ListExpression();
            for (Expression e : le.getExpressions()) {
                result.addExpression(transformInlineConstants(e));
            }
            return result;
        } else if (exp instanceof AnnotationConstantExpression) {
            ConstantExpression ce = (ConstantExpression) exp;
            if (ce.getValue() instanceof AnnotationNode) {
                // replicate a little bit of AnnotationVisitor here
                // because we can't wait until later to do this
                AnnotationNode an = (AnnotationNode) ce.getValue();
                for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
                    member.setValue(transformInlineConstants(member.getValue()));
                }

            }
        }
        return exp;
    }

    private void checkAnnotationMemberValue(Expression newValue) {
        if (newValue instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) newValue;
            if (!(pe.getObjectExpression() instanceof ClassExpression)) {
                addError("unable to find class '" + pe.getText() + "' for annotation attribute constant", pe.getObjectExpression());
            }
        } else if (newValue instanceof ListExpression) {
            ListExpression le = (ListExpression) newValue;
            for (Expression e : le.getExpressions()) {
                checkAnnotationMemberValue(e);
            }
        }
    }

    @Override
    public void visitClass(ClassNode node) {
        ClassNode oldNode = currentClass;

        if (node instanceof InnerClassNode) {
            if (Modifier.isStatic(node.getModifiers())) {
                genericParameterNames = new HashMap<String, GenericsType>();
            }
        } else {
            genericParameterNames = new HashMap<String, GenericsType>();
        }
        currentClass = node;
        resolveGenericsHeader(node.getGenericsTypes());

        ModuleNode module = node.getModule();
        if (!module.hasImportsResolved()) {
            for (ImportNode importNode : module.getImports()) {
                currImportNode = importNode;
                ClassNode type = importNode.getType();
                if (resolve(type, false, false, true)) {
                    currImportNode = null;
                    continue;
                }
                currImportNode = null;
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticStarImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, false, false, true)) {
                    continue;
                }
                // Maybe this type belongs in the same package as the node that is doing the
                // static import. In that case, the package may not have been explicitly specified.
                // Try with the node's package too. If still not found, revert to original type name.
                if (type.getPackageName() == null && node.getPackageName() != null) {
                    String oldTypeName = type.getName();
                    type.setName(node.getPackageName() + "." + oldTypeName);
                    if (resolve(type, false, false, true)) {
                        continue;
                    }
                    type.setName(oldTypeName);
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, true, true, true)) {
                    continue;
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticStarImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, true, true, true)) {
                    continue;
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            module.setImportsResolved(true);
        }

        ClassNode sn = node.getUnresolvedSuperClass();
        if (sn != null) {
            resolveOrFail(sn, node, true);
        }

        for (ClassNode anInterface : node.getInterfaces()) {
            resolveOrFail(anInterface, node, true);
        }

        checkCyclicInheritance(node, node.getUnresolvedSuperClass(), node.getInterfaces());

        super.visitClass(node);

        currentClass = oldNode;
    }

    private void checkCyclicInheritance(ClassNode originalNode, ClassNode parentToCompare, ClassNode[] interfacesToCompare) {
        if (!originalNode.isInterface()) {
            if (parentToCompare == null) {
                return;
            }
            if (originalNode == parentToCompare.redirect()) {
                addError("Cyclic inheritance involving " + parentToCompare.getName() + " in class " + originalNode.getName(), originalNode);
                return;
            }
            if (interfacesToCompare != null && interfacesToCompare.length > 0) {
                for (ClassNode intfToCompare : interfacesToCompare) {
                    if (originalNode == intfToCompare.redirect()) {
                        addError("Cycle detected: the type " + originalNode.getName() + " cannot implement itself", originalNode);
                        return;
                    }
                }
            }
            if (parentToCompare == ClassHelper.OBJECT_TYPE) {
                return;
            }
            checkCyclicInheritance(originalNode, parentToCompare.getUnresolvedSuperClass(), null);
        } else {
            if (interfacesToCompare != null && interfacesToCompare.length > 0) {
                // check interfaces at this level first
                for (ClassNode intfToCompare : interfacesToCompare) {
                    if (originalNode == intfToCompare.redirect()) {
                        addError("Cyclic inheritance involving " + intfToCompare.getName() + " in interface " + originalNode.getName(), originalNode);
                        return;
                    }
                }
                // check next level of interfaces
                for (ClassNode intf : interfacesToCompare) {
                    checkCyclicInheritance(originalNode, null, intf.getInterfaces());
                }
            } else {
                return;
            }
        }
    }

    @Override
    public void visitCatchStatement(CatchStatement cs) {
        resolveOrFail(cs.getExceptionType(), cs);
        if (cs.getExceptionType() == ClassHelper.DYNAMIC_TYPE) {
            cs.getVariable().setType(ClassHelper.make(Exception.class));
        }
        super.visitCatchStatement(cs);
    }

    @Override
    public void visitForLoop(ForStatement forLoop) {
        resolveOrFail(forLoop.getVariableType(), forLoop);
        super.visitForLoop(forLoop);
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {
        VariableScope oldScope = currentScope;
        currentScope = block.getVariableScope();
        super.visitBlockStatement(block);
        currentScope = oldScope;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return source;
    }

    private boolean resolveGenericsTypes(GenericsType[] types) {
        if (types == null) {
            return true;
        }
        currentClass.setUsingGenerics(true);
        boolean resolved = true;
        for (GenericsType type : types) {
            // attempt resolution on all types, so don't short-circuit and stop if we've previously failed
            resolved = resolveGenericsType(type) && resolved;
        }
        return resolved;
    }

    private void resolveGenericsHeader(GenericsType[] types) {
        if (types == null) {
            return;
        }
        currentClass.setUsingGenerics(true);
        for (GenericsType type : types) {
            ClassNode classNode = type.getType();
            String name = type.getName();
            ClassNode[] bounds = type.getUpperBounds();
            if (bounds != null) {
                boolean nameAdded = false;
                for (ClassNode upperBound : bounds) {
                    if (!nameAdded && upperBound != null || !resolve(classNode)) {
                        genericParameterNames.put(name, type);
                        type.setPlaceholder(true);
                        classNode.setRedirect(upperBound);
                        nameAdded = true;
                    }
                    resolveOrFail(upperBound, classNode);
                }
            } else {
                genericParameterNames.put(name, type);
                classNode.setRedirect(ClassHelper.OBJECT_TYPE);
                type.setPlaceholder(true);
            }
        }
    }

    private boolean resolveGenericsType(GenericsType genericsType) {
        if (genericsType.isResolved()) {
            return true;
        }
        currentClass.setUsingGenerics(true);
        ClassNode type = genericsType.getType();
        // save name before redirect
        String name = type.getName();
        ClassNode[] bounds = genericsType.getUpperBounds();
        if (!genericParameterNames.containsKey(name)) {
            if (bounds != null) {
                for (ClassNode upperBound : bounds) {
                    resolveOrFail(upperBound, genericsType);
                    type.setRedirect(upperBound);
                    resolveGenericsTypes(upperBound.getGenericsTypes());
                }
            } else if (genericsType.isWildcard()) {
                type.setRedirect(ClassHelper.OBJECT_TYPE);
            } else {
                resolveOrFail(type, genericsType);
            }
        } else {
            GenericsType gt = genericParameterNames.get(name);
            type.setRedirect(gt.getType());
            genericsType.setPlaceholder(true);
        }

        if (genericsType.getLowerBound() != null) {
            resolveOrFail(genericsType.getLowerBound(), genericsType);
        }

        if (resolveGenericsTypes(type.getGenericsTypes())) {
            genericsType.setResolved(genericsType.getType().isResolved());
        }
        return genericsType.isResolved();

    }

    @Override
    public void setClassNodeResolver(ClassNodeResolver classNodeResolver) {
        this.classNodeResolver = classNodeResolver;
    }
}
