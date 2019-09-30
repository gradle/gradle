/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.binaryinfo

import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures

import java.util.regex.Matcher
import java.util.regex.Pattern

class ReadelfBinaryInfo implements BinaryInfo {

    private final File binaryFile
    private final List<String> environments

    ReadelfBinaryInfo(File binaryFile, List<String> environments) {
        this.environments = environments
        this.binaryFile = binaryFile
    }

    static boolean canUseReadelf() {
        def process = ['readelf', '-v'].execute()
        return process.waitFor() && process.exitValue() == 0
    }

    ArchitectureInternal getArch() {
        def process = ['readelf', '-h', binaryFile.absolutePath].execute()
        List<String> lines = process.inputStream.readLines()
        return readArch(lines)
    }

    List<String> listObjectFiles() {
        def process = ['ar', '-t', binaryFile.getAbsolutePath()].execute()
        return process.inputStream.readLines()
    }

    List<String> listLinkedLibraries() {
        def process = ['readelf', '-d', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        return lines
    }

    List<Symbol> listSymbols() {
        return NMToolFixture.of(environments).listSymbols(binaryFile);
    }

    @Override
    List<Symbol> listDebugSymbols() {
        def process = ['readelf', '--debug-dump=info', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        def symbols = []

        lines.each { line ->
            def findSymbol = (line =~ /.*DW_AT_name\s+:\s+(\(.*\):)?\s+(.*)/)
            if (findSymbol.matches()) {
                def name = new File(findSymbol[0][2] as String).name.trim()
                symbols << new Symbol(name, 'D' as char, true)
            }
        }
        return symbols
    }

    String getSoName() {
        def process = ['readelf', '-d', binaryFile.absolutePath].execute()
        List<String> lines = process.inputStream.readLines()
        return readSoName(lines)
    }

    static String readSoName(List<String> lines) {
        final Pattern pattern = ~/^.*\(SONAME\)\s+.*soname.*\: \[(.*)\]$/
        String matchingLine = lines.find {
            pattern.matcher(it).matches()
        }
        if (matchingLine == null) {
            return null;
        }
        final Matcher matcher = pattern.matcher(matchingLine)
        assert matcher.matches()
        return matcher.group(1)
    }

    static ArchitectureInternal readArch(List<String> lines) {
        def archString = readFirstHeaderValue(lines, "Machine:", "Maschine:")
        switch (archString) {
            case "Intel 80386":
                return Architectures.forInput("x86")
            case "Advanced Micro Devices X86-64":
                return Architectures.forInput("x86_64")
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}\nreadelf output:\n${lines}")
        }
    }

    private static String readFirstHeaderValue(List<String> lines, String... headers) {
        def matchingLines = headers.collect { header ->
            String matchingLine = lines.find {
                it.trim().startsWith(header)
            }
            matchingLine?.replaceFirst(header, "")?.trim()
        }
        return matchingLines.find { it != null }
    }
}
