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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ObjectArrays;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.ResolveVisitor;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * A Gradle version of the Groovy {@link ResolveVisitor} that resolves Gradle default imports.
 * <p>
 * Instead of adding every default-imported Gradle package as a star import, which makes Groovy probe each package for every unresolved name,
 * it looks the simple name up in a precomputed mapping from simple name to fully qualified class name.
 *
 * @since 2.12
 */
public class GradleResolveVisitor extends ResolveVisitor {
    // Groovy's default imports (java.lang, java.util, java.io, java.net, groovy.lang, groovy.util) and java.time.
    // This is a copy of Groovy's array on purpose, see resolveFromDefaultImports.
    // TODO: sync with the documentation in writing_build_scripts_intermediate.adoc
    private static final String[] DEFAULT_IMPORT_PACKAGES = ObjectArrays.concat(DEFAULT_IMPORTS, "java.time.");

    /**
     * When updating this mapping, consider being nice and also updating the list of generated imports for our internal test infrastructure.
     * See the import list in AnnotationGenerator.kt
     */
    private static final ImmutableMap<String, ClassNode> TYPE_REDIRECT_MAPPING = ImmutableMap.of(
        "BigInteger", ClassHelper.BigInteger_TYPE,
        "BigDecimal", ClassHelper.BigDecimal_TYPE,
        "Inject", ClassHelper.makeCached(Inject.class)
    );

    private final CompilationUnit compilationUnit;
    private final Map<String, List<String>> simpleNameToFQN;
    private ClassNodeResolver classNodeResolver = new ClassNodeResolver();
    private @Nullable ClassNode currentClass;
    private boolean inPropertyChain;

    public GradleResolveVisitor(CompilationUnit compilationUnit, Map<String, List<String>> simpleNameToFQN) {
        super(compilationUnit);
        this.compilationUnit = compilationUnit;
        this.simpleNameToFQN = simpleNameToFQN;
    }

    @Override
    public void setClassNodeResolver(ClassNodeResolver classNodeResolver) {
        super.setClassNodeResolver(classNodeResolver);
        this.classNodeResolver = classNodeResolver;
    }

    @Override
    public void visitClass(ClassNode node) {
        ClassNode oldClass = currentClass;
        currentClass = node;
        try {
            super.visitClass(node);
        } finally {
            currentClass = oldClass;
        }
    }

    @Override
    protected boolean resolveNestedClass(ClassNode type) {
        // Classes declared in a script are top-level classes, so the script class has no nested types.
        // Its Gradle base classes have no nested types meant for scripts either, so don't probe "Base$Name" for every name in the script.
        // isScript() is also true for any class extending groovy.lang.Script, only the generated script class is a script body
        //
        // Groovy also marks closure and lambda classes generated for the script as script bodies. This doesn't affect resolution:
        // those classes are only created during class generation, long after resolving. While resolving, closures and lambdas are
        // expressions inside the script class, so the current class is the script class and the reasoning above applies to them.
        // Anonymous classes are different: the parser creates them, so they are resolved as classes of their own, and they are not script bodies.
        if (currentClass != null && currentClass.isScriptBody()) {
            return false;
        }
        return super.resolveNestedClass(type);
    }

    @Override
    protected Expression transformPropertyExpression(PropertyExpression pe) {
        boolean oldInPropertyChain = inPropertyChain;
        inPropertyChain = true;
        try {
            return super.transformPropertyExpression(pe);
        } finally {
            inPropertyChain = oldInPropertyChain;
        }
    }

    @Override
    protected boolean resolveFromStaticInnerClasses(ClassNode type) {
        // Groovy resolves "a.b.C.D" as a nested class by replacing dots with "$" one by one from the right: "a.b.C$D", "a.b$C$D" and so on.
        // Property chains in scripts like "rootProject.ext.libs.VERSION" go through the same steps when they don't resolve to a class,
        // and every step is a class loader lookup. Classes with lower case names that have nested classes are rare,
        // so in property chains stop at a lower case segment, except for the first step, to allow for a nested class of a lower case class.
        // Only chains without a prefix that resolves to a class are affected: Groovy also resolves "a.B.c.D" by resolving "a.B" first.
        // Types declared in expressions inside a chain, like "new a.b.c.D()" in a closure, still resolve: Groovy tries every split for them without recursion.
        // Imports and other types are resolved outside property chains, so they are not limited.
        String name = type.getName();
        if (inPropertyChain && name.indexOf('$') >= 0) {
            int lastDot = name.lastIndexOf('.');
            int segmentStart = name.lastIndexOf('.', lastDot - 1) + 1;
            if (Character.isLowerCase(name.charAt(segmentStart))) {
                return false;
            }
        }
        return super.resolveFromStaticInnerClasses(type);
    }

    @Override
    protected boolean resolveFromDefaultImports(ClassNode type) {
        String name = type.getName();
        List<String> fqn = simpleNameToFQN.get(name);
        if (fqn != null && resolveFromResolver(type, fqn.get(0))) {
            return true;
        }
        // Names starting with a lower case letter are not looked up in default imports, the same way Groovy does it.
        // Groovy only skips plain names, but also names like "project$VERSION", produced when resolving a property chain
        // like "project.VERSION" as a nested class, cannot be classes in the default packages
        if (Character.isLowerCase(name.charAt(0))) {
            return false;
        }
        // Groovy's own lookup records the package of every resolved name in a JVM-wide cache, and its values are not thread-safe sets.
        // Scripts can be compiled concurrently, for example when Isolated Projects configures projects in parallel, which can corrupt them.
        // Groovy only updates the cache when given its own DEFAULT_IMPORTS array, so pass a copy and don't use that cache at all.
        if (resolveFromDefaultImports(type, DEFAULT_IMPORT_PACKAGES)) {
            return true;
        }
        ClassNode redirect = TYPE_REDIRECT_MAPPING.get(name);
        if (redirect != null) {
            type.setRedirect(redirect);
            return true;
        }
        return false;
    }

    private boolean resolveFromResolver(ClassNode type, String name) {
        ClassNodeResolver.LookupResult result = classNodeResolver.resolveName(name, compilationUnit);
        if (result != null && result.isClassNode()) {
            type.setRedirect(result.getClassNode());
            return true;
        }
        return false;
    }
}
