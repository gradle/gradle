package org.gradle.junit;

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.internal.builders.JUnit4Builder;

public class MyBuilder extends AllDefaultPossibilitiesBuilder {
	public MyBuilder(boolean canUseSuiteMethod) {
		super(canUseSuiteMethod);
	}

	@Override
	protected JUnit4Builder junit4Builder() {
		return new MyJunit4Builder();
	}
}
