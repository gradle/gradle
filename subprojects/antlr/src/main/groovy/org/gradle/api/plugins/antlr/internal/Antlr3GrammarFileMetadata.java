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
package org.gradle.api.plugins.antlr.internal;

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Metadata associated with ANTLR v3 source files.
 */
public class Antlr3GrammarFileMetadata {

    private static final Pattern BASENAME_PATTERN = Pattern.compile("([^*?]+)(\\.[gG])");
    
    private String baseName;
    private File sourceFile;
    
    public Antlr3GrammarFileMetadata(File sourceFile) {
        this.sourceFile = sourceFile;
        Matcher m = BASENAME_PATTERN.matcher(sourceFile.getName());
        if (m.find()) {
            baseName = m.group(1);
        } else {
            throw new IllegalArgumentException("unrecognized file extension for '"
                + sourceFile.getName()
                + "', expected .g or .G");
        }
    }

    public String getLexerFileName() {
        return baseName + "Lexer.java";
    }

    public String getParserFileName() {
        return baseName + "Parser.java";
    }

    public String getTokenFileName() {
        return baseName + ".tokens";
    }
}