/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

@ServiceScope(Scope.UserHome.class)
public class ClasspathElementTransformFactoryForAgent implements ClasspathElementTransformFactory {

    private static final int AGENT_INSTRUMENTATION_VERSION = 3;

    private final ClasspathBuilder classpathBuilder;
    private final ClasspathWalker classpathWalker;

    public ClasspathElementTransformFactoryForAgent(ClasspathBuilder classpathBuilder, ClasspathWalker classpathWalker) {
        this.classpathBuilder = classpathBuilder;
        this.classpathWalker = classpathWalker;
    }

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putInt(AGENT_INSTRUMENTATION_VERSION);
    }

    @Override
    public ClasspathElementTransform createTransformer(File file, ClassTransform classTransform) {
        return new ClasspathElementTransformForAgent(file, classpathBuilder, classpathWalker, classTransform);
    }

    @Override
    public String toString() {
        return "TransformFactory(agent)";
    }
}
