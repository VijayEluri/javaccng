/* Copyright (c) 2006, Sun Microsystems, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.javacc.parser;

import org.javacc.utils.JavaFileGenerator;
import org.javacc.utils.io.IndentingPrintWriter;

import java.io.File;
import java.io.IOException;

/** Generate boilerplate java files. */
public class JavaFiles implements FileGenerator, JavaCCParserConstants {
  @Override
  public void start() throws IOException {
    generateScanner();
    generateToken();
    generateScannerException();
    generateParseException();
    generateCharStream();
    if (Options.getJavaUnicodeEscape()) {
      generateJavaCharStream();
    }
    else {
      generateSimpleCharStream();
    }
  }

  public void generateScanner() throws IOException {
    generate("/templates/Scanner.template", "Scanner.java");
  }

  public void generateToken() throws IOException {
    generate("/templates/Token.template", "Token.java");
  }

  public void generateScannerException() throws IOException {
    generate("/templates/ScannerException.template", "ScannerException.java");
  }

  public void generateParseException() throws IOException {
    generate("/templates/ParseException.template", "ParseException.java");
  }

  public void generateCharStream() throws IOException {
    generate("/templates/CharStream.template", "CharStream.java");
  }

  public void generateSimpleCharStream() throws IOException {
    generate("/templates/SimpleCharStream.template", "SimpleCharStream.java");
  }

  public void generateJavaCharStream() throws IOException {
    generate("/templates/JavaCharStream.template", "JavaCharStream.java");
  }

  private static void generate(String templateName, String fileName) throws IOException {
    File path = new File(Options.getOutputDirectory(), fileName);
    OutputFile outputFile = new OutputFile(path);
    IndentingPrintWriter out = outputFile.getPrintWriter();
    try {
      TokenPrinter.packageDeclaration(JavaCCGlobals.cuToInsertionPoint1, out);
      JavaFileGenerator generator = new JavaFileGenerator(
          templateName, Options.getOptions());
      generator.generate(out);
    }
    finally {
      out.close();
    }
  }
}
