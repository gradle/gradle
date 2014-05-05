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

package org.gradle.nativebinaries.language.rc.plugins

import org.gradle.nativebinaries.toolchain.Clang
import org.gradle.nativebinaries.toolchain.Gcc
import org.gradle.nativebinaries.toolchain.ToolChainRegistry
import org.gradle.nativebinaries.toolchain.VisualCpp
import org.gradle.util.TestUtil
import spock.lang.Specification

/**
 * Created by Rene on 28/04/14.
 */
class WindowsResourcesPluginTest extends Specification {

    final def project = TestUtil.createRootProject()

    def "registers rcCompiler tool to visualcpp"() {
        when:
        project.configure(project){
            apply plugin: WindowsResourcesPlugin
            executables {
                exe {}
            }
            nativeLibraries {
                lib {}
            }
        }
        then:
        ToolChainRegistry toolChains = project.modelRegistry.get("toolChains", ToolChainRegistry)
        toolChains.withType(VisualCpp){ VisualCpp visualCpp ->
            visualCpp.getByName("rcCompiler") != null
        }
        toolChains.withType(Gcc){ Gcc gcc ->
            gcc.findByName("rcCompiler") == null
        }
        toolChains.withType(Clang){ Clang clang ->
            clang.findByName("rcCompiler") == null
        }
    }
}
