/*
 * Copyright 2018 the original author or authors.
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
/**
 * Binary information for GCC produced binaries. It approximate features required by our tests using dumpbin.exe from Visual Studio. It's not the right solution, but it works for most cases.
 */
class DumpbinGccProducedBinaryInfo extends DumpbinBinaryInfo {
    private final List<String> environments

    DumpbinGccProducedBinaryInfo(File binaryFile, List<String> environments) {
        super(binaryFile)
        this.environments = environments
    }

    @Override
    List<Symbol> listSymbols() {
        // With VS2019, dumpbin is not able to properly list the headers of a MinGW which prevent us from asserting the presence or not of debug symbols. For this, we started to migrate toward using the Linux tools within the MinGW installation.
        return NMToolFixture.of(environments).listSymbols(binaryFile)
    }

    void assertHasDebugSymbols() {
        assert listSymbols().find { it.name.contains(".debug_line") } != null
    }

    void assertDoesNotHaveDebugSymbols() {
        assert listSymbols().find { it.name.contains(".debug_line") } == null
    }
}
