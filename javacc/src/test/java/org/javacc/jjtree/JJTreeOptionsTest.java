package org.javacc.jjtree;

import org.javacc.parser.JavaCCErrors;
import org.javacc.parser.Options;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Test the JJTree-specific options.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public final class JJTreeOptionsTest {
  @Test
  public void testOutputDirectory() {
    JJTreeOptions.init();
    JavaCCErrors.reInit();

    assertEquals(new File("."), JJTreeOptions.getOutputDirectory());
    assertEquals(new File("."), JJTreeOptions.getJJTreeOutputDirectory());

    Options.setInputFileOption(null, null, "OUTPUT_DIRECTORY",
        "test/output");
    assertEquals(new File("test/output"), JJTreeOptions.getOutputDirectory());
    assertEquals(new File("test/output"), JJTreeOptions.getJJTreeOutputDirectory());

    Options.setInputFileOption(null, null, "JJTREE_OUTPUT_DIRECTORY",
        "test/jjtreeoutput");
    assertEquals(new File("test/output"), JJTreeOptions.getOutputDirectory());
    assertEquals(new File("test/jjtreeoutput"), JJTreeOptions.getJJTreeOutputDirectory());

    assertEquals(0, JavaCCErrors.getWarningCount());
    assertEquals(0, JavaCCErrors.getErrorCount());
    assertEquals(0, JavaCCErrors.getParseErrorCount());
    assertEquals(0, JavaCCErrors.getSemanticErrorCount());
  }

  @Test
  public void testNodeFactory() {
    JJTreeOptions.init();
    JavaCCErrors.reInit();

    assertEquals(0, JavaCCErrors.getWarningCount());
    assertEquals(0, JavaCCErrors.getErrorCount());
    JJTreeOptions.setInputFileOption(null, null, "NODE_FACTORY", Boolean.FALSE);
    assertEquals(JJTreeOptions.getNodeFactory(), "");

    JJTreeOptions.init();
    JJTreeOptions.setInputFileOption(null, null, "NODE_FACTORY", Boolean.TRUE);
    assertEquals(JJTreeOptions.getNodeFactory(), "*");

    JJTreeOptions.init();
    JJTreeOptions.setInputFileOption(null, null, "NODE_FACTORY", "mypackage.MyNode");
    assertEquals(JJTreeOptions.getNodeFactory(), "mypackage.MyNode");

    assertEquals(0, JavaCCErrors.getWarningCount());

    assertEquals(0, JavaCCErrors.getErrorCount());
    assertEquals(0, JavaCCErrors.getParseErrorCount());
    assertEquals(0, JavaCCErrors.getSemanticErrorCount());
  }

  @Test
  public void testNodeClass() {
    JJTreeOptions.init();
    JavaCCErrors.reInit();

    assertEquals(0, JavaCCErrors.getWarningCount());
    assertEquals(0, JavaCCErrors.getErrorCount());

    assertEquals("", JJTreeOptions.getNodeClass());
    // Need some functional tests, as well.
  }

  @Test
  public void testValidate() {
    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=Object");
    JJTreeOptions.validate();
    assertEquals(1, JavaCCErrors.getWarningCount());

    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=Object");
    JJTreeOptions.setCmdLineOption("VISITOR=true");
    JJTreeOptions.validate();
    assertEquals(0, JavaCCErrors.getWarningCount());

    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=Object");
    JJTreeOptions.validate();
    assertEquals(1, JavaCCErrors.getWarningCount());
  }

  @Test
  public void testValidateReturnType() {
    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=String");
    JJTreeOptions.validate();
    assertEquals(1, JavaCCErrors.getWarningCount());

    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=String");
    JJTreeOptions.setCmdLineOption("VISITOR=true");
    JJTreeOptions.validate();
    assertEquals(0, JavaCCErrors.getWarningCount());

    JJTreeOptions.init();
    JavaCCErrors.reInit();

    JJTreeOptions.setCmdLineOption("VISITOR_DATA_TYPE=String");
    JJTreeOptions.validate();
    assertEquals(1, JavaCCErrors.getWarningCount());
  }
}
