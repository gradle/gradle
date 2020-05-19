
package org.gradle.test;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.util.ReleaseInfo;

import java.util.List;

/**
 * Searches through all classes to be compiled for a field named
 * "groovycVersion". If such a field is found, a field initializer is added
 * and the search ends. The initializer sets the field to a String containing
 * the groovyc version detected (currently "1.6"). Any existing initializer
 * is overridden. If the field's type is not compatible with String, a compile-
 * time or runtime error will occur.
 * Because transforms have only been introduced in Groovy 1.6, this transform
 * will have no effect on classes compiled with earlier Groovy versions.
 */
@SuppressWarnings("unchecked")
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
public class DetectorTransform implements ASTTransformation {
  private static final String VERSION_FIELD_NAME = "groovycVersion";

  public void visit(ASTNode[] nodes, SourceUnit source) {
    if (nodes.length == 0 || !(nodes[0] instanceof ModuleNode)) {
      source.getErrorCollector().addError(new SimpleMessage(
        "internal error in DetectorTransform", source));
      return;
    }
    ModuleNode module = (ModuleNode)nodes[0];
    for (ClassNode clazz : (List<ClassNode>)module.getClasses()) {
      FieldNode field = clazz.getField(VERSION_FIELD_NAME);
      if (field != null) {
        field.setInitialValueExpression(new ConstantExpression(ReleaseInfo.getVersion()));
        break;
      }
    }
  }
}