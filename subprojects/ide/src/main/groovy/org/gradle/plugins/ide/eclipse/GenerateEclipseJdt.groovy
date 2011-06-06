/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.JavaVersion
import org.gradle.api.internal.ClassGenerator
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.Jdt
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator
import org.gradle.util.DeprecationLogger

/**
 * Generates the Eclipse JDT configuration file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseJdt}.
 */
class GenerateEclipseJdt extends GeneratorTask<Jdt> {

    /**
     * Eclipse project model that contains information needed for this task
     */
    EclipseJdt jdt

    /**
     * Deprecated. Please use #eclipse.jdt.sourceCompatibility. See examples in {@link EclipseJdt}.
     * <p>
     * The source Java language level.
     */
    @Deprecated
    JavaVersion getSourceCompatibility() {
        DeprecationLogger.nagUser("eclipseJdt.sourceCompatibility", "eclipse.jdt.sourceCompatibility")
        jdt.sourceCompatibility
    }

    @Deprecated
    void setSourceCompatibility(Object sourceCompatibility) {
        DeprecationLogger.nagUser("eclipseJdt.sourceCompatibility", "eclipse.jdt.sourceCompatibility")
        jdt.sourceCompatibility = sourceCompatibility
    }

    /**
     * Deprecated. Please use #eclipse.jdt.targetCompatibility. See examples in {@link EclipseJdt}.
     * <p>
     * The target JVM to generate {@code .class} files for.
     */
    @Deprecated
    JavaVersion getTargetCompatibility() {
        DeprecationLogger.nagUser("eclipseJdt.targetCompatibility", "eclipse.jdt.targetCompatibility")
        jdt.targetCompatibility
    }

    @Deprecated
    void setTargetCompatibility(Object targetCompatibility) {
        DeprecationLogger.nagUser("eclipseJdt.targetCompatibility", "eclipse.jdt.targetCompatibility")
        jdt.targetCompatibility = targetCompatibility
    }

    GenerateEclipseJdt() {
        generator = new PersistableConfigurationObjectGenerator<Jdt>() {
            Jdt create() {
                return new Jdt()
            }

            void configure(Jdt jdtContent) {
                def jdtModel = getJdt()
                jdtModel.file.beforeMerged.execute(jdtContent)
                jdtContent.sourceCompatibility = jdtModel.sourceCompatibility
                jdtContent.targetCompatibility = jdtModel.targetCompatibility
                jdtModel.file.whenMerged.execute(jdtContent)
            }
        }
        jdt = services.get(ClassGenerator).newInstance(EclipseJdt)
    }
}
