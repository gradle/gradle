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

/**
 * This project at the bottom of the project hierarchy enforce the "platform" for the Gradle distribution.
 * We want the versions that are packaged in the distribution to be used everywhere (e.g. in all test scenarios)
 * Hence, we lock the versions down here for all other subprojects.
 *
 * Note:
 * We use strictly here because we do not have any better means to do this at the moment.
 * Ideally we wound be able to say "lock down all the versions of the dependencies resolved for the distribution"
 */
plugins {
    base
}
dependencies {
    constraints {
        libraries.keys.forEach { libId ->
            add("default", library(libId)) {
                version {
                    strictly(libraryVersion(libId))
                }
                because(libraryReason(libId))
            }
        }

        // Reject dependencies we do not want completely
        add("default", "org.sonatype.sisu:sisu-inject-plexus") {
            version { rejectAll() }
            because("We do not wand this dependency injection on the classpath")
        }
    }
}



