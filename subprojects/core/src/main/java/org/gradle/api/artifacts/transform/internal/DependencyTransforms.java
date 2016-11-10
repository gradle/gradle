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

package org.gradle.api.artifacts.transform.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.DependencyTransform;
import org.gradle.api.artifacts.transform.TransformInput;
import org.gradle.api.artifacts.transform.TransformOutput;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class DependencyTransforms {
    private final List<DependencyTransformRegistration> transforms = Lists.newArrayList();

    public Transformer<File, File> getTransform(String from, String to) {
        for (DependencyTransformRegistration transformReg : transforms) {
            if (transformReg.from.equals(from) && transformReg.to.equals(to)) {
                return transformReg.getTransformer();
            }
        }
        return null;
    }

    public void registerTransform(Class<? extends DependencyTransform> type, Action<? super DependencyTransform> config) {
        TransformInput transformInput = type.getAnnotation(TransformInput.class);
        if (transformInput == null) {
            throw new RuntimeException("DependencyTransform must statically declare input type using `@TransformInput`");
        }
        String from = transformInput.format();

        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                TransformOutput transformOutput = method.getAnnotation(TransformOutput.class);
                if (transformOutput == null || method.getParameterTypes().length > 0 || !method.getReturnType().equals(File.class)) {
                    continue;
                }

                String to = transformOutput.format();
                JavaMethod<? super DependencyTransform, File> javaMethod = JavaReflectionUtil.method(File.class, method);
                DependencyTransformRegistration registration = new DependencyTransformRegistration(from, to, type, javaMethod, config);
                transforms.add(registration);
            }
        }
    }

    private final class DependencyTransformRegistration {
        final String from;
        final String to;
        final Class<? extends DependencyTransform> type;
        final JavaMethod<? super DependencyTransform, File> outputProperty;
        final Action<? super DependencyTransform> config;

        public DependencyTransformRegistration(String from, String to, Class<? extends DependencyTransform> type, JavaMethod<? super DependencyTransform, File> outputProperty, Action<? super DependencyTransform> config) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.outputProperty = outputProperty;
            this.config = config;
        }

        public Transformer<File, File> getTransformer() {
            DependencyTransform dependencyTransform = DirectInstantiator.INSTANCE.newInstance(type);
            config.execute(dependencyTransform);
            return new DependencyTransformTransformer(dependencyTransform, outputProperty);
        }
    }

    private static class DependencyTransformTransformer implements Transformer<File, File> {
        private final DependencyTransform dependencyTransform;
        private final JavaMethod<? super DependencyTransform, File> outputProperty;

        private DependencyTransformTransformer(DependencyTransform dependencyTransform, JavaMethod<? super DependencyTransform, File> outputProperty) {
            this.dependencyTransform = dependencyTransform;
            this.outputProperty = outputProperty;
        }

        @Override
        public File transform(File file) {
            if (dependencyTransform.getOutputDirectory() != null) {
                dependencyTransform.getOutputDirectory().mkdirs();
            }
            dependencyTransform.transform(file);
            return outputProperty.invoke(dependencyTransform);
        }
    }

}
