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

package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.gradleWrapper
import common.javaHome
import common.requiresOs
import common.uuidPrefix
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import vcsroots.useAbsoluteVcs

object PublishKotlinDslPlugin : BuildType({
    name = "Publish Kotlin DSL Plugin"
    id("Util_PublishKotlinDslPlugin")
    uuid = "${DslContext.uuidPrefix}_Util_PublishKotlinDslPlugin"
    vcs.useAbsoluteVcs(VersionedSettingsBranch.fromDslContext().vcsRootId())

    requirements {
        requiresOs(Os.LINUX)
    }

    params {
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        param("env.GRADLE_PUBLISH_KEY", "%plugin.portal.publish.key%")
        param("env.GRADLE_PUBLISH_SECRET", "%plugin.portal.publish.secret%")
        param("env.PGP_SIGNING_KEY", "%pgpSigningKey%")
        param("env.PGP_SIGNING_KEY_PASSPHRASE", "%pgpSigningPassphrase%")
    }
    steps {
        gradleWrapper {
            name = "Publish Kotlin DSL Plugin"
            tasks = "clean :kotlin-dsl-plugins:publishPlugins --no-configuration-cache"
        }
    }
})
