package org.gradle.internal.upgrade;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ApiUpgradeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUpgradeManager.class);

    private static final Type[] EMPTY = {};

    private final List<Replacement> replacements = new ArrayList<>();

    public void init() {
        for (Replacement replacement : replacements) {
            replacement.initializeReplacement();
        }
        new ApiUpgradeHandler(ImmutableList.copyOf(replacements)).useInstance();
    }

    public interface MethodReplacer<T> {
        <T> void replaceWith(ReplacementLogic<T> method);
    }

    public <T> MethodReplacer<T> matchMethod(Type type, Type returnType, String methodName, Type... argumentTypes) {
        return new MethodReplacer<T>() {
            @Override
            public <T> void replaceWith(ReplacementLogic<T> replacement) {
                replacements.add(new MethodReplacement<>(type, returnType, methodName, argumentTypes, replacement));
            }
        };
    }

    interface PropertyReplacer<T, P> {
        void replaceWith(Function<? super T, ? extends P> getter, BiConsumer<? super T, ? super P> setter);
    }

    public <T, P> PropertyReplacer<T, P> matchProperty(Class<T> type, Class<P> propertyType, String propertyName) {
        return new PropertyReplacer<T, P>() {
            @Override
            public void replaceWith(
                Function<? super T, ? extends P> getterReplacement,
                BiConsumer<? super T, ? super P> setterReplacement
            ) {
                String capitalizedPropertyName = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
                String getterPrefix = propertyType.equals(boolean.class)
                    ? "is"
                    : "get";
                addGetterReplacement(type, propertyType, getterPrefix + capitalizedPropertyName, getterReplacement);
                addSetterReplacement(type, propertyType, "set" + capitalizedPropertyName, setterReplacement);
                addGroovyPropertyReplacement(type, propertyType, propertyName, getterReplacement, setterReplacement);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addGetterReplacement(Class<T> type, Class<P> propertyType, String getterName, Function<? super T, ? extends P> getterReplacement) {
        replacements.add(new MethodReplacement<P>(
            Type.getType(type),
            Type.getType(propertyType),
            getterName,
            new Type[]{},
            (receiver, arguments) -> getterReplacement.apply((T) receiver)));
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addSetterReplacement(Class<T> type, Class<P> propertyType, String setterName, BiConsumer<? super T, ? super P> setterReplacement) {
        replacements.add(new MethodReplacement<Void>(
            Type.getType(type),
            Type.VOID_TYPE,
            setterName,
            new Type[]{Type.getType(propertyType)},
            (receiver, arguments) -> {
                setterReplacement.accept((T) receiver, (P) arguments[0]);
                return null;
            }));
    }

    private <T, V> void addGroovyPropertyReplacement(Class<T> type, Class<V> propertyType, String propertyName, Function<? super T, ? extends V> getterReplacement, BiConsumer<? super T, ? super V> setterReplacement) {
        replacements.add(new DynamicGroovyPropertyReplacement<>(type, propertyType, propertyName, getterReplacement, setterReplacement));
    }

    public void implementReplacements(String className) throws IOException, ReflectiveOperationException {
        String type = className.replace('.', '/');
        LOGGER.info("Transforming " + type);
        byte[] classBytes = Resources.toByteArray(Resources.getResource(type + ".class"));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        implementReplacements(classLoader, classBytes);
    }

    public void implementReplacements(ClassLoader classLoader, byte[] classBytes) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // creates the ASM ClassReader which will read the class file
        ClassReader classReader = new ClassReader(classBytes);
        // creates the ASM ClassWriter which will create the transformed class
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        // creates the ClassVisitor to do the byte code transformations
        ClassVisitor classVisitor = new ApiUpgraderClassVisitor(replacements, classWriter);
        // reads the class file and apply the transformations which will be written into the ClassWriter
        classReader.accept(classVisitor, 0);

        // gets the bytes from the transformed class
        byte[] bytes = classWriter.toByteArray();
        // writes the transformed class to the file system - to analyse it (e.g. javap -verbose)
//        File out = new File("build/" + type.getClassName() + "\\$Transformed.class");
//        new FileOutputStream(out).write(bytes);
//        ASMifier.main(new String[]{out.getAbsolutePath()});

        // inject the transformed class into the current class loader
        Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        defineClass.invoke(classLoader, null, bytes, 0, bytes.length);
    }
}
