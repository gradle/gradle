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

package org.gradle.api.internal.model;

import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import groovy.lang.GroovyObject;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ManagedFactory;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.model.internal.asm.ClassGeneratorSuffixRegistry;
import org.gradle.model.internal.asm.ClassVisitorScope;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector;
import org.gradle.model.internal.inspect.ValidationProblemCollector;
import org.gradle.model.internal.type.ModelType;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.V1_5;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

@ServiceScope(Scope.Global.class)
public class NamedObjectInstantiator implements ManagedFactory {
    private static final int FACTORY_ID = Objects.hashCode(Named.class.getName());
    private static final Type OBJECT = getType(Object.class);
    private static final Type STRING = getType(String.class);
    private static final Type CLASS_GENERATING_LOADER = getType(ClassGeneratingLoader.class);
    private static final Type MANAGED = getType(Managed.class);
    private static final String[] INTERFACES_FOR_ABSTRACT_CLASS = {MANAGED.getInternalName()};
    private static final String RETURN_VOID = getMethodDescriptor(Type.VOID_TYPE);
    private static final String RETURN_STRING = getMethodDescriptor(STRING);
    private static final String RETURN_CLASS = getMethodDescriptor(getType(Class.class));
    private static final String RETURN_BOOLEAN = getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_OBJECT = getMethodDescriptor(OBJECT);
    private static final String RETURN_INT = getMethodDescriptor(Type.INT_TYPE);
    private static final String RETURN_VOID_FROM_STRING = getMethodDescriptor(Type.VOID_TYPE, STRING);
    private static final String RETURN_OBJECT_FROM_STRING = getMethodDescriptor(OBJECT, STRING);
    private static final String NAME_FIELD = "_gr_name_";
    private static final String CONSTRUCTOR_NAME = "<init>";

    private final CrossBuildInMemoryCache<Class<?>, LoadingCache<String, Object>> generatedTypes;
    private final String implSuffix;
    private final String factorySuffix;
    private final Function<Class<?>, LoadingCache<String, Object>> cacheFactoryFunction = this::cacheFactory;

    public NamedObjectInstantiator(CrossBuildInMemoryCacheFactory cacheFactory) {
        implSuffix = ClassGeneratorSuffixRegistry.assign("$Impl");
        factorySuffix = ClassGeneratorSuffixRegistry.assign(implSuffix + "Factory");
        generatedTypes = cacheFactory.newClassMap();
    }

    @Override
    public int getId() {
        return FACTORY_ID;
    }

    @Override
    public <T> T fromState(Class<T> type, Object state) {
        return Named.class.isAssignableFrom(type)
            ? uncheckedCast(named(uncheckedCast(type), (String) state))
            : null;
    }

    public <T extends Named> T named(final Class<T> type, final String name) throws ObjectInstantiationException {
        try {
            return type.cast(generatedTypes.get(type, cacheFactoryFunction).getUnchecked(name));
        } catch (UncheckedExecutionException e) {
            throw new ObjectInstantiationException(type, e.getCause());
        } catch (Exception e) {
            throw new ObjectInstantiationException(type, e);
        }
    }

    private ClassGeneratingLoader loaderFor(Class<?> publicClass) {

        validate(publicClass);

        //
        // Generate implementation class
        //
        Type implementationType = generateImplementationClassFor(publicClass);

        //
        // Generate factory class
        //
        Class<Object> factoryClass = generateFactoryClassFor(publicClass, implementationType);
        try {
            return (ClassGeneratingLoader) factoryClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void validate(Class<?> publicClass) {
        FormattingValidationProblemCollector problemCollector = new FormattingValidationProblemCollector("Named implementation class", ModelType.of(publicClass));
        visitFields(publicClass, problemCollector);
        if (problemCollector.hasProblems()) {
            throw new GradleException(problemCollector.format());
        }
    }

    private Type generateImplementationClassFor(Class<?> publicClass) {
        Type publicType = getType(publicClass);
        Type superClass;
        String[] interfaces;
        if (publicClass.isInterface()) {
            superClass = OBJECT;
            interfaces = new String[]{publicType.getInternalName(), MANAGED.getInternalName()};
        } else {
            superClass = publicType;
            interfaces = INTERFACES_FOR_ABSTRACT_CLASS;
        }

        AsmClassGenerator generator = new AsmClassGenerator(publicClass, implSuffix);
        new ClassVisitorScope(generator.getVisitor()) {{

            String generatedTypeName = generator.getGeneratedType().getInternalName();

            visit(V1_5, ACC_PUBLIC | ACC_SYNTHETIC, generatedTypeName, null, superClass.getInternalName(), interfaces);

            //
            // Add `name` field
            //
            addField(ACC_PRIVATE, NAME_FIELD, STRING);

            //
            // Add constructor
            //
            publicMethod(CONSTRUCTOR_NAME, RETURN_VOID_FROM_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // Call this.super()
                _ALOAD(0);
                _INVOKESPECIAL(superClass, CONSTRUCTOR_NAME, RETURN_VOID);
                // Set this.name = param1
                _ALOAD(0);
                _ALOAD(1);
                _PUTFIELD(generatedTypeName, NAME_FIELD, STRING);
                // Done
                _RETURN();
            }});

            //
            // Add `getName()`
            //
            publicMethod("getName", RETURN_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // return this.name
                _ALOAD(0);
                _GETFIELD(generatedTypeName, NAME_FIELD, STRING);
                _ARETURN();
            }});

            //
            // Add `toString()`
            //
            publicMethod("toString", RETURN_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // return this.name
                _ALOAD(0);
                _GETFIELD(generatedTypeName, NAME_FIELD, STRING);
                _ARETURN();
            }});

            //
            // Add `Object unpackState() { return name }`
            //
            publicMethod("unpackState", RETURN_OBJECT, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _GETFIELD(generatedTypeName, NAME_FIELD, STRING);
                _ARETURN();
            }});

            //
            // Add `publicType`
            //
            publicMethod("publicType", RETURN_CLASS, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(publicType);
                _ARETURN();
            }});

            //
            // Add `boolean isImmutable() { return true }`
            //
            publicMethod("isImmutable", RETURN_BOOLEAN, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(true);
                _IRETURN_OF(BOOLEAN_TYPE);
            }});
            //
            // Add `getFactoryId()`
            //
            publicMethod("getFactoryId", RETURN_INT, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(FACTORY_ID);
                _IRETURN();
            }});

            visitEnd();
        }};

        generator.define();
        return generator.getGeneratedType();
    }

    private Class<Object> generateFactoryClassFor(Class<?> publicClass, Type implementationType) {
        AsmClassGenerator generator = new AsmClassGenerator(publicClass, factorySuffix);
        new ClassVisitorScope(generator.getVisitor()) {{
            visit(V1_5, ACC_PUBLIC | ACC_SYNTHETIC, generator.getGeneratedType().getInternalName(), null, CLASS_GENERATING_LOADER.getInternalName(), null);

            //
            // Add constructor
            //
            publicMethod(CONSTRUCTOR_NAME, RETURN_VOID, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // super();
                _ALOAD(0);
                _INVOKESPECIAL(CLASS_GENERATING_LOADER, CONSTRUCTOR_NAME, RETURN_VOID);
                _RETURN();
            }});

            //
            // Add factory method
            //
            publicMethod("load", RETURN_OBJECT_FROM_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // Call return new <implClass>(param1)
                _NEW(implementationType);
                _DUP();
                _ALOAD(1);
                _INVOKESPECIAL(implementationType, CONSTRUCTOR_NAME, RETURN_VOID_FROM_STRING);
                _ARETURN();
            }});

            visitEnd();
        }};
        return generator.define();
    }

    private void visitFields(Class<?> type, ValidationProblemCollector collector) {
        if (type.equals(Object.class)) {
            return;
        }
        if (type.getSuperclass() != null) {
            visitFields(type.getSuperclass(), collector);
        }

        // Disallow instance fields. This doesn't guarantee that the object is immutable, just makes it less likely
        // We might tighten this constraint to also disallow any _code_ on immutable types that reaches out to static state
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || (GroovyObject.class.isAssignableFrom(type) && field.getName().equals("metaClass"))) {
                continue;
            }
            collector.add(field, "A Named implementation class must not define any instance fields.");
        }
    }

    private LoadingCache<String, Object> cacheFactory(Class<?> type) {
        return CacheBuilder.newBuilder().build(loaderFor(type));
    }

    protected abstract static class ClassGeneratingLoader extends CacheLoader<String, Object> {
        @Override
        public abstract Object load(String name);
    }
}
