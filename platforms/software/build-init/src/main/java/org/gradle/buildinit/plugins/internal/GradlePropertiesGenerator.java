/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GradlePropertiesGenerator implements BuildContentGenerator {

    public static void generate(InitSettings settings) {
        File file = settings.getTarget().file("gradle.properties").getAsFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            if (settings.isWithComments()) {
                writer.println("# This file was generated by the Gradle 'init' task.");
                writer.println("# https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties");
                writer.println();
            }
            writer.println("org.gradle.configuration-cache=true");
            if (settings.isUseIncubatingAPIs()) {
                writer.println("org.gradle.parallel=true");
                writer.println("org.gradle.caching=true");
            }
            writer.println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        generate(settings);
    }
}
