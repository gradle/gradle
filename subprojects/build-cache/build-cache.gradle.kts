import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2017 the original author or authors.
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
plugins {
    `java-library`
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")

}

dependencies {
    api(project(":baseServices"))
    api(library("inject"))

    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":persistentCache"))
    implementation(project(":resources"))
    implementation(project(":logging"))

    implementation(library("commons_io"))

    jmh(library("ant")) {
        version {
            prefer(libraryVersion("ant"))
        }
    }

    jmh(library("commons_compress")) {
        version {
            prefer(libraryVersion("commons_compress"))
        }
    }

    jmh("io.airlift:aircompressor:0.8")
    jmh("org.iq80.snappy:snappy:0.4")
    jmh("org.kamranzafar:jtar:2.3")
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}


testFixtures {
    from(":core")
    from(":baseServices")
}
