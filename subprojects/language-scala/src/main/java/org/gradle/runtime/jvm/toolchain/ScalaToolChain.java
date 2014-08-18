package org.gradle.runtime.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * A set of tools for building from Java source.
 *
 * <p>A {@code JavaToolChain} is able to:
 *
 * <ul>
 *
 * <li>Get a Java toolchain</li>
 *
 * <li>Fetch Scala zinc compiler</li>
 *
 * <li>Compile Scala source to bytecode.</li>
 *
 * <li>Generate Scaladoc from Scala source.</li>
 *
 * </ul>
 */
@Incubating
@HasInternalProtocol
public class ScalaToolChain {
}
