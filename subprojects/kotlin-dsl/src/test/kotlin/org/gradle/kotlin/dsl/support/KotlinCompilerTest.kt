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

package org.gradle.kotlin.dsl.support

import org.gradle.api.JavaVersion
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.jetbrains.kotlin.config.JvmTarget
import org.junit.Test


class KotlinCompilerTest {

    @Test
    fun `Gradle JavaVersion to Kotlin JvmTarget direct conversion`() {
        assertThat(JavaVersion.VERSION_19.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_19))
        assertThat(JavaVersion.VERSION_18.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_18))
        assertThat(JavaVersion.VERSION_17.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_17))
        assertThat(JavaVersion.VERSION_16.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_16))
        assertThat(JavaVersion.VERSION_15.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_15))
        assertThat(JavaVersion.VERSION_14.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_14))
        assertThat(JavaVersion.VERSION_13.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_13))
        assertThat(JavaVersion.VERSION_12.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_12))
        assertThat(JavaVersion.VERSION_11.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_11))
        assertThat(JavaVersion.VERSION_1_10.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_10))
        assertThat(JavaVersion.VERSION_1_9.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_9))
        assertThat(JavaVersion.VERSION_1_8.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_1_8))
    }

    @Test
    fun `Gradle JavaVersion lesser than 8 to Kotlin JvmTarget conversion`() {
        JavaVersion.values().filter { it < JavaVersion.VERSION_1_8 }.forEach { javaVersion ->
            assertThat(javaVersion.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_1_8))
        }
    }

    @Test
    fun `Gradle JavaVersion greater than 19 to Kotlin JvmTarget conversion`() {
        JavaVersion.values().filter { it > JavaVersion.VERSION_19 }.forEach { javaVersion ->
            assertThat(javaVersion.toKotlinJvmTarget(), equalTo(JvmTarget.JVM_19))
        }
    }
}
