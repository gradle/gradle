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

package org.gradle.api.internal.provider;

import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class DefaultProvider<T> extends AbstractMinimalProvider<T> {
    private final Callable<? extends T> value;
    private boolean inspected;

    public DefaultProvider(Callable<? extends T> value) {
        this.value = value;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        if (!inspected) {
            inspected = true;
            System.out.println("INSPECTING " + value.getClass());
            URL resource = value.getClass().getClassLoader().getResource(value.getClass().getName().replace(".", "/") + ".class");
            if (resource != null) {
                System.out.println("-> reading bytecode from " + resource);
                try {
                    try (InputStream inputStream = resource.openConnection().getInputStream()) {
                        File outFile = new File("inspected/" + value.getClass().getName() + ".class");
                        Files.createDirectories(outFile.getParentFile().toPath());
                        Files.copy(inputStream, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("-> wrote bytecode to " + outFile);
                        ClassReader reader = new ClassReader(Files.readAllBytes(outFile.toPath()));
                        reader.accept(new ClassVisitor(Opcodes.ASM7) {
                            @Override
                            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                System.out.println("-> class name = " + name);
                                System.out.println("-> interfaces = " + Arrays.toString(interfaces));
                                System.out.println("-> signature = " + signature);
                            }

                            @Override
                            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                                System.out.println("-> inner class attribute for " + name);
                                System.out.println("-> outer class " + outerName);
                                System.out.println("-> inner class " + innerName);
                            }
                        }, ClassReader.SKIP_CODE);
                    }
                } catch (IOException ex) {
                    throw UncheckedException.throwAsUncheckedException(ex);
                }
                try {
                    System.out.println("-> inner types: " + Arrays.asList(value.getClass().getDeclaredClasses()));
                    System.out.println("-> enclosing class: " + value.getClass().getEnclosingClass());
                    System.out.println("-> enclosing method: " + value.getClass().getEnclosingMethod());
                    System.out.println("-> generic interfaces: " + Arrays.asList(value.getClass().getGenericInterfaces()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("-> CAN'T FIND BYTECODE");
            }
        }

        // We could do a better job of figuring this out
        // Extract the type for common case that is quick to calculate
        for (Type superType : value.getClass().getGenericInterfaces()) {
            if (superType instanceof ParameterizedType) {
                ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
                if (parameterizedSuperType.getRawType().equals(Callable.class)) {
                    Type argument = parameterizedSuperType.getActualTypeArguments()[0];
                    if (argument instanceof Class) {
                        return Cast.uncheckedCast(argument);
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected Value<? extends T> calculateOwnValue() {
        try {
            return Value.ofNullable(value.call());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
