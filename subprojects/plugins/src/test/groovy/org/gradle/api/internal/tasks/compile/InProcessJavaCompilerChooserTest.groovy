package org.gradle.api.internal.tasks.compile

import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.api.tasks.compile.CompileOptions

class InProcessJavaCompilerChooserTest extends Specification {
    def chooser = new InProcessJavaCompilerChooser()
    def options = new CompileOptions()
    
    @Requires(TestPrecondition.JDK6)
    def "chooses JDK 6 compiler on JDK 6"() {
        expect:
        chooser.choose(options).getClass().name == "org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler"
    }

    @Requires(TestPrecondition.JDK5)
    def "chooses Sun compiler on JDK 5"() {
        expect:
        chooser.choose(options) instanceof SunJavaCompiler
    }
}
