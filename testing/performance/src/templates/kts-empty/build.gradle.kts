/*
 * Copyright 2016 the original author or authors.
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

<% if (binding.hasVariable("buildScanPluginVersion") || binding.hasVariable("springDmPluginVersion")) {%>

buildscript {
    repositories {
<% if(binding.hasVariable("springDmPluginVersion")) { %>
        mavenLocal()
        mavenCentral()
<% } %>
<% if (binding.hasVariable("buildScanPluginVersion")) { %>
        maven {
            setUrl("https://repo.gradle.org/gradle/gradlecom-libs-snapshots-local")
        }
<% } %>
    }

    dependencies {
<% if (binding.hasVariable("buildScanPluginVersion")) { %>
        classpath("com.gradle:build-scan-plugin:${buildScanPluginVersion}")
<% }%>
<% if(binding.hasVariable("springDmPluginVersion")) { %>
        classpath("io.spring.gradle:dependency-management-plugin:$springDmPluginVersion")
<% }%>
    }
}
<% if (binding.hasVariable("buildScanPluginVersion")) { %>
apply(plugin = "com.gradle.build-scan")
// TODO: fix buildScan configuration
// buildScan { licenseAgreementUrl = 'https://gradle.com/terms-of-service'; licenseAgree = 'yes' }
<% }%>
<% } %>
