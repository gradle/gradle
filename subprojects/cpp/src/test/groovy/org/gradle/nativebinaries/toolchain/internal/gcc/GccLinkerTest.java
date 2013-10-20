package org.gradle.nativebinaries.toolchain.internal.gcc;

import static org.junit.Assert.*;

import org.junit.Test;

public class GccLinkerTest {

	@Test
	public void test() {
		assertEquals("somelib", GccLinker.getLibraryName("libsomelib.so"));
	}
}
