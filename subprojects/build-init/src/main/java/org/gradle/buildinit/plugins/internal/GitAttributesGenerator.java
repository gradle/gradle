/*
 * Copyright 2019 the original author or authors.
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

public class GitAttributesGenerator implements BuildContentGenerator {

    @Override
    public void generate(InitSettings settings) {
        File file = settings.getTarget().file(".gitattributes").getAsFile();
        try {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("#");
                writer.println("# https://help.github.com/articles/dealing-with-line-endings/");
                writer.println("#");
                writer.println("# These are explicitly windows files and should use crlf");
                writer.println("*.bat           text eol=crlf");
                writer.println();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
