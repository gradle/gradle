/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.tasks.apigen.abi;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * This class lists all packages which are available through the Jigsaw "java.base" module. The list has been generated using the followig Groovy script:
 * <pre><code>
 *     import groovy.util.XmlParser
 *
 *     def url = new URL('http://hg.openjdk.java.net/jdk9/jdk9/raw-file/156eb4ef93f1/modules.xml')
 *
 *     def modules = new XmlParser().parse(url.newInputStream())
 *     modules.module
 *        .find { it.name.text() == 'java.base' }
 *        .export
 *        .findAll { !it.to }
 *        .name*.text()
 *        .collect { "\"$it\"" }
 *        .join(',\n')
 * </code></pre>
 */
abstract class JavaBaseModule {
    public static final List<String> PACKAGES = ImmutableList.of(
        "java.io",
        "java.lang",
        "java.lang.annotation",
        "java.lang.invoke",
        "java.lang.ref",
        "java.lang.reflect",
        "java.math",
        "java.net",
        "java.nio",
        "java.nio.channels",
        "java.nio.channels.spi",
        "java.nio.charset",
        "java.nio.charset.spi",
        "java.nio.file",
        "java.nio.file.attribute",
        "java.nio.file.spi",
        "java.security",
        "java.security.acl",
        "java.security.cert",
        "java.security.interfaces",
        "java.security.spec",
        "java.text",
        "java.text.spi",
        "java.time",
        "java.time.chrono",
        "java.time.format",
        "java.time.temporal",
        "java.time.zone",
        "java.util",
        "java.util.concurrent",
        "java.util.concurrent.atomic",
        "java.util.concurrent.locks",
        "java.util.function",
        "java.util.jar",
        "java.util.regex",
        "java.util.spi",
        "java.util.stream",
        "java.util.zip",
        "javax.crypto",
        "javax.crypto.interfaces",
        "javax.crypto.spec",
        "javax.net",
        "javax.net.ssl",
        "javax.security.auth",
        "javax.security.auth.callback",
        "javax.security.auth.login",
        "javax.security.auth.spi",
        "javax.security.auth.x500",
        "javax.security.cert",
        "jdk",
        "jdk.net"
    );
}
