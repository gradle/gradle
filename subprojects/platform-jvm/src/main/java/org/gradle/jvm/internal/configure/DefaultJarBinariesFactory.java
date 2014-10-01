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

package org.gradle.jvm.internal.configure;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.DefaultJarBinarySpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.internal.BinaryNamingScheme;

public class DefaultJarBinariesFactory implements JarBinariesFactory { //TODO: NativeBInariesFactory and this one should extend a BaseClass (BinariesFactory) that creates binaries and adds them on component specs based on the configure action - parameter should be a Factory that creates the specific type (JarBinarySpec here)
    private final Instantiator instantiator;
    private final Action<? super JarBinarySpec> configureAction;

    public DefaultJarBinariesFactory(Instantiator instantiator, Action<? super JarBinarySpec> configureAction) {
        this.instantiator = instantiator;
        this.configureAction = configureAction;
    }

    public void createJarBinaries(JvmLibrarySpec jvmLibrary, BinaryNamingScheme namingScheme, JavaToolChain toolChain, JavaPlatform platform) {
        DefaultJarBinarySpec jarBinary = instantiator.newInstance(DefaultJarBinarySpec.class, jvmLibrary, namingScheme, toolChain, platform);
        setupDefaults(jarBinary);
        jarBinary.source(jvmLibrary.getSource());
        jvmLibrary.getBinaries().add(jarBinary);
    }

    private void setupDefaults(DefaultJarBinarySpec jarBinary) {
        configureAction.execute(jarBinary);
    }
}
