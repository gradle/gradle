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

package org.gradle.play;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.platform.base.ApplicationBinarySpec;
import org.gradle.platform.base.Variant;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.toolchain.PlayToolChain;

import java.io.File;
import java.util.Map;

/**
 * Represents a binary artifact that is the result of building a Play application software component.
 */
@Incubating
@HasInternalProtocol
public interface PlayApplicationBinarySpec extends ApplicationBinarySpec {
    /**
     * {@inheritDoc}
     */
    @Override
    PlayApplicationSpec getApplication();

    /**
     * The PlayPlatform this binary is built for.
     * @return platform for this binary
     */
    @Variant
    PlayPlatform getTargetPlatform();

    PlayToolChain getToolChain();

    /**
     * The application jar file produced for this binary.
     * @return the application jar file
     */
    File getJarFile();

    /**
     * The assets jar file produced for this binary.
     * @return the assets jar file
     */
    File getAssetsJarFile();

    /**
     * A buildable object representing the class files and resources that will be included in the application jar file.
     * @return the JvmClasses for this binary
     */
    // TODO: Replace this with `JvmAssembly` once that type is public
    JvmClasses getClasses();

    /**
     * A buildable object representing the public assets that will be included in the assets jar file.
     * @return the PublicAssets for this binary
     */
    PublicAssets getAssets();

    Map<LanguageSourceSet, ScalaLanguageSourceSet> getGeneratedScala();

    Map<LanguageSourceSet, JavaScriptSourceSet> getGeneratedJavaScript();
}
