/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.javadoc.internal.JavadocGenerator;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JvmPlatform;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultJavaToolChain implements JavaToolChainInternal {
    private final JavaCompilerFactory compilerFactory;
    private final ExecActionFactory execActionFactory;
    private final JavaVersion javaVersion;

    public DefaultJavaToolChain(JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory) {
        this.compilerFactory = compilerFactory;
        this.execActionFactory = execActionFactory;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("JDK%s", javaVersion);
    }

    public String getDisplayName() {
        return String.format("current JDK (%s)", javaVersion);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public ToolProvider select(JvmPlatform targetPlatform) {
        // TODO:DAZ Remove all of the calls to this method with null platform
        if (targetPlatform != null && targetPlatform.getTargetCompatibility().compareTo(javaVersion) > 0) {
            return new UnavailableToolProvider(targetPlatform);
        }
        return new JavaToolProvider();
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    private boolean isCompatible(JvmPlatform platform, JavaVersion version) {
        return platform.getTargetCompatibility().compareTo(version) <= 0; //TODO freekh: need something smarter here when dealing with toolchains or perhaps a platform should define which toolchains it is compatible with so users can override this functionality by overriding the platform?
    }

    // TODO:DAZ Remove this method: check availability in the binary initializer, throw failure when creating compiler in task
    //TODO freekh: remove this method:
    public void assertValidPlatform(JvmPlatform platform, PlatformContainer platforms) {
        List<JvmPlatform> alternatives = new ArrayList<JvmPlatform>();
        alternatives.addAll(platforms.withType(JvmPlatform.class));
        Collections.sort(alternatives, new Comparator<JvmPlatform>() {
            public int compare(JvmPlatform p1, JvmPlatform p2) {
                return -p1.getTargetCompatibility().compareTo(p2.getTargetCompatibility());
            }
        });

        if (!isCompatible(platform, getJavaVersion())) {
            List<String> compatibleVersions = new ArrayList<String>();
            for (JvmPlatform alternative: alternatives) {
                if (isCompatible(alternative, getJavaVersion())) {
                    compatibleVersions.add(alternative.getName());
                }
            }

            String compatibleVersionsString = compatibleVersions.isEmpty() ? "(None)" : compatibleVersions.toString();
            throw new IllegalArgumentException(String.format("Cannot use target JVM platform: '"+platform.getName()+"' with target compatibility '"+platform.getTargetCompatibility()+"' because it is too high compared to Java toolchain version '"+getJavaVersion()+"'. Compatible target platforms are: " + compatibleVersionsString + "."));
        }
    }

    private class JavaToolProvider implements ToolProvider {
        public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
            if (spec instanceof JavaCompileSpec) {
                CompileOptions options = ((JavaCompileSpec) spec).getCompileOptions();
                @SuppressWarnings("unchecked") Compiler<T> compiler = (Compiler<T>) compilerFactory.create(options);
                return compiler;
            }
            if (spec instanceof JavadocSpec) {
                @SuppressWarnings("unchecked") Compiler<T> compiler = (Compiler<T>) new JavadocGenerator(execActionFactory);
                return compiler;
            }

            throw new IllegalArgumentException(String.format("Don't know how to compile using spec of type %s.", spec.getClass().getSimpleName()));
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private class UnavailableToolProvider implements ToolProvider {
        private final JvmPlatform targetPlatform;

        private UnavailableToolProvider(JvmPlatform targetPlatform) {
            this.targetPlatform = targetPlatform;
        }

        public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
            throw new IllegalArgumentException(getMessage());
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(getMessage());
        }

        private String getMessage() {
            // TODO:DAZ Use display names here
            return String.format("Could not use target JVM platform: '%s' when using JDK: '%s'.", targetPlatform.getTargetCompatibility(), javaVersion);
        }
    }
}
