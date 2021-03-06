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

import org.javacc.utils.io.IndentingPrintWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/** Generate lexer. */
final class ScannerGen implements FileGenerator {
  private final JavaCCState state;
  // Hashtable of vectors
  Hashtable<String, List<TokenProduction>> allTpsForState
      = new Hashtable<String, List<TokenProduction>>();
  int lexStateIndex;
  int[] kinds;
  int maxOrdinal = 1;
  String lexStateSuffix;
  String[] newLexState;
  int[] lexStates;
  boolean[] ignoreCase;
  Action[] actions;
  Hashtable<String, NfaState> initStates
      = new Hashtable<String, NfaState>();
  int stateSetSize;
  int maxLexStates;
  String[] lexStateName;
  NfaState[] singlesToSkip;
  long[] toSkip;
  long[] toSpecial;
  long[] toMore;
  long[] toToken;
  int defaultLexState;
  RegularExpression[] rexprs;
  int[] maxLongsReqd;
  int[] initMatch;
  int[] canMatchAnyChar;
  boolean hasEmptyMatch;
  boolean[] canLoop;
  boolean[] stateHasActions;
  boolean hasLoop;
  boolean[] canReachOnMore;
  boolean[] hasNfa;
  boolean[] mixed;
  NfaState initialState;
  int curKind;
  boolean hasSkipActions;
  boolean hasMoreActions;
  boolean hasTokenActions;
  boolean hasSpecial;
  boolean hasSkip;
  boolean hasMore;
  RegularExpression curRE;
  boolean keepLineCol;
  public static boolean keepImage;
  final NfaStates nfaStates = new NfaStates();
  final StringLiterals stringLiterals = new StringLiterals();

  ScannerGen(JavaCCState state) {
    this.state = state;
  }

  @Override
  public void start() throws MetaParseException, IOException {
    if (JavaCCErrors.getErrorCount() != 0) {
      throw new MetaParseException();
    }

    if (!Options.getBuildScanner()
        || Options.getUserScanner()) {
      return;
    }

    File path = new File(Options.getOutputDirectory(), state.scannerClass() + ".java");
    OutputFile outputFile = new OutputFile(path);
    IndentingPrintWriter out = outputFile.getPrintWriter();
    try {
      generate(out);
    }
    finally {
      out.close();
    }
  }

  private void generate(IndentingPrintWriter out) throws IOException {
    keepLineCol = Options.getKeepLineColumn();
    keepImage = Options.getKeepImage();
    ArrayList choices = new ArrayList();

    printClassHead(out);
    buildLexStatesTable();

    boolean ignoring;

    Enumeration<String> e = allTpsForState.keys();
    while (e.hasMoreElements()) {
      nfaStates.reInit();
      stringLiterals.reInit();

      String key = e.nextElement();

      lexStateIndex = getIndex(key);
      lexStateSuffix = "_" + lexStateIndex;
      List<TokenProduction> allTps = allTpsForState.get(key);
      initStates.put(key, initialState = new NfaState(this));
      ignoring = false;

      singlesToSkip[lexStateIndex] = new NfaState(this);
      singlesToSkip[lexStateIndex].dummy = true;

      if (key.equals("DEFAULT")) {
        defaultLexState = lexStateIndex;
      }

      for (int i = 0; i < allTps.size(); i++) {
        TokenProduction tp = allTps.get(i);
        int kind = tp.kind;
        boolean ignore = tp.ignoreCase;
        List<RegExpSpec> reSpecs = tp.reSpecs;

        if (i == 0) {
          ignoring = ignore;
        }

        for (RegExpSpec reSpec : reSpecs) {
          curRE = reSpec.regExp;

          rexprs[curKind = curRE.ordinal] = curRE;
          lexStates[curRE.ordinal] = lexStateIndex;
          ignoreCase[curRE.ordinal] = ignore;

          if (curRE.isPrivate) {
            kinds[curRE.ordinal] = -1;
            continue;
          }

          if (curRE instanceof RStringLiteral
              && !((RStringLiteral) curRE).image.equals("")) {
            ((RStringLiteral) curRE).generateDfa(this);
            if (i != 0 && !mixed[lexStateIndex] && ignoring != ignore) {
              mixed[lexStateIndex] = true;
            }
          }
          else if (curRE.canMatchAnyChar()) {
            if (canMatchAnyChar[lexStateIndex] == -1
                || canMatchAnyChar[lexStateIndex] > curRE.ordinal) {
              canMatchAnyChar[lexStateIndex] = curRE.ordinal;
            }
          }
          else {
            Nfa temp;

            if (curRE instanceof RChoice) {
              choices.add(curRE);
            }

            temp = curRE.generateNfa(this, ignore);
            temp.end.isFinal = true;
            temp.end.kind = curRE.ordinal;
            initialState.addMove(temp.start);
          }

          if (kinds.length < curRE.ordinal) {
            int[] tmp = new int[curRE.ordinal + 1];

            System.arraycopy(kinds, 0, tmp, 0, kinds.length);
            kinds = tmp;
          }

          kinds[curRE.ordinal] = kind;

          if (reSpec.nextState != null &&
              !reSpec.nextState.equals(lexStateName[lexStateIndex])) {
            newLexState[curRE.ordinal] = reSpec.nextState;
          }

          if (reSpec.action != null && reSpec.action.getActionTokens() != null &&
              reSpec.action.getActionTokens().size() > 0) {
            actions[curRE.ordinal] = reSpec.action;
          }

          switch (kind) {
            case TokenProduction.SPECIAL:
              hasSkipActions |= (actions[curRE.ordinal] != null) ||
                  (newLexState[curRE.ordinal] != null);
              hasSpecial = true;
              toSpecial[curRE.ordinal / 64] |= 1L << (curRE.ordinal % 64);
              curRE.toSpecial = true;
              toSkip[curRE.ordinal / 64] |= 1L << (curRE.ordinal % 64);
              curRE.toSkip = true;
              break;
            case TokenProduction.SKIP:
              hasSkipActions |= (actions[curRE.ordinal] != null);
              hasSkip = true;
              toSkip[curRE.ordinal / 64] |= 1L << (curRE.ordinal % 64);
              curRE.toSkip = true;
              break;
            case TokenProduction.MORE:
              hasMoreActions |= (actions[curRE.ordinal] != null);
              hasMore = true;
              toMore[curRE.ordinal / 64] |= 1L << (curRE.ordinal % 64);
              curRE.toMore = true;

              if (newLexState[curRE.ordinal] != null) {
                canReachOnMore[getIndex(newLexState[curRE.ordinal])] = true;
              }
              else {
                canReachOnMore[lexStateIndex] = true;
              }

              break;
            case TokenProduction.TOKEN:
              hasTokenActions |= (actions[curRE.ordinal] != null);
              toToken[curRE.ordinal / 64] |= 1L << (curRE.ordinal % 64);
              curRE.toToken = true;
              break;
          }
        }
      }

      // Generate a static block for initializing the nfa transitions
      nfaStates.computeClosures();

      for (int i = 0; i < initialState.epsilonMoves.size(); i++) {
        ((NfaState) initialState.epsilonMoves.elementAt(i)).generateCode();
      }

      if (hasNfa[lexStateIndex] = (nfaStates.generatedStates != 0)) {
        initialState.generateCode();
        initialState.generateInitMoves(out);
      }

      if (initialState.kind != Integer.MAX_VALUE && initialState.kind != 0) {
        if ((toSkip[initialState.kind / 64] & (1L << initialState.kind)) != 0L
            || (toSpecial[initialState.kind / 64] & (1L << initialState.kind)) != 0L) {
          hasSkipActions = true;
        }
        else if ((toMore[initialState.kind / 64] & (1L << initialState.kind)) != 0L) {
          hasMoreActions = true;
        }
        else {
          hasTokenActions = true;
        }

        if (initMatch[lexStateIndex] == 0 ||
            initMatch[lexStateIndex] > initialState.kind) {
          initMatch[lexStateIndex] = initialState.kind;
          hasEmptyMatch = true;
        }
      }
      else if (initMatch[lexStateIndex] == 0) {
        initMatch[lexStateIndex] = Integer.MAX_VALUE;
      }

      stringLiterals.FillSubString(this);

      if (hasNfa[lexStateIndex] && !mixed[lexStateIndex]) {
        stringLiterals.generateNfaStartStates(this, out, initialState);
      }

      stringLiterals.dumpDfaCode(this, out);

      if (hasNfa[lexStateIndex]) {
        nfaStates.dumpMoveNfa(this, out);
      }

      if (stateSetSize < nfaStates.generatedStates) {
        stateSetSize = nfaStates.generatedStates;
      }
    }

    for (Object choice : choices) {
      ((RChoice) choice).checkUnmatchability(this);
    }

    nfaStates.dumpStateSets(out);
    checkEmptyStringMatch();
    nfaStates.dumpNonAsciiMoveMethods(out);
    stringLiterals.dumpStrLiteralImages(this, out);
    dumpStaticVarDeclarations(out);
    dumpMakeToken(out);
    dumpGetNextToken(out);

    if (Options.getDebugScanner()) {
      nfaStates.dumpStatesForKind(out);
      dumpDebugMethods(out);
    }

    if (hasLoop) {
      out.println("int[] jjEmptyLineNo = new int[" + maxLexStates + "];");
      out.println("int[] jjEmptyColumnNo = new int[" + maxLexStates + "];");
      out.println("boolean[] jjBeenHere = new boolean[" + maxLexStates + "];");
    }

    if (hasSkipActions) {
      dumpSkipActions(out);
    }
    if (hasMoreActions) {
      dumpMoreActions(out);
    }
    if (hasTokenActions) {
      dumpTokenActions(out);
    }

    nfaStates.printBoilerPlate(out);
    out.unindent();
    out.println("}");
  }

  private void printClassHead(IndentingPrintWriter out)
      throws IOException {
    int i, j;

    TokenPrinter tp = new TokenPrinter();

    int l = 0, kind;
    i = 1;
    List<Token> tokens = state.cuToInsertionPoint1;
    while (true) {
      if (tokens.size() <= l) {
        break;
      }

      kind = tokens.get(l).getKind();
      if (kind == JavaCCConstants.PACKAGE || kind == JavaCCConstants.IMPORT) {
        for (; i < tokens.size(); i++) {
          kind = tokens.get(i).getKind();
          if (kind == JavaCCConstants.SEMICOLON
              || kind == JavaCCConstants.ABSTRACT
              || kind == JavaCCConstants.FINAL
              || kind == JavaCCConstants.PUBLIC
              || kind == JavaCCConstants.CLASS
              || kind == JavaCCConstants.INTERFACE) {
            tp.line = tokens.get(l).getLine();
            tp.column = tokens.get(l).getColumn();
            for (j = l; j < i; j++) {
              tp.printToken(tokens.get(j), out);
            }
            if (kind == JavaCCConstants.SEMICOLON) {
              tp.printToken(tokens.get(j), out);
            }
            out.println();
            break;
          }
        }
        l = ++i;
      }
      else {
        break;
      }
    }

    out.println();
    out.println("@SuppressWarnings(\"unused\")");
    out.print("public class " + state.scannerClass() + " implements Scanner, " +
        state.constantsClass() + " {");

    writeScannerDeclarations(out);

    out.indent();

    out.println();

    if (Options.getDebugScanner()) {
      out.println("/** Debug output. */");
      out.println("private java.io.PrintWriter debugPrinter = new java.io.PrintWriter(System.out);");
      out.println();
      out.println("/** Set debug output. */");
      out.println("public void setDebugPrinter(java.io.PrintWriter printer) { debugPrinter = printer; }");
      out.println();
    }

    if (Options.getScannerUsesParser()) {
      out.println();
      out.println("/** The parser. */");
      out.println("protected " + state.parserClass() + " parser;");
    }
  }

  private void writeScannerDeclarations(IndentingPrintWriter out)
      throws IOException {
    List<Token> tokens = state.scannerDeclarations;
    if (tokens != null
        && tokens.size() > 0) {
      boolean commonTokenActionSeen = false;

      TokenPrinter tp = new TokenPrinter();

      tp.setup(tokens.get(0));
      tp.column = 1;

      for (Token t : tokens) {
        if (t.getKind() == JavaCCConstants.IDENTIFIER
            && Options.getCommonTokenAction()
            && !commonTokenActionSeen) {
          commonTokenActionSeen = t.getImage().equals("commonTokenAction");
        }

        tp.printToken(t, out);
      }

      out.println();

      if (Options.getCommonTokenAction() && !commonTokenActionSeen) {
        JavaCCErrors.warning("You have the COMMON_TOKEN_ACTION option set. " +
            "But it appears you have not defined the method :\n" +
            "      void commonTokenAction(Token t)\n" +
            "in your SCANNER_DECLS. The generated scanner will not compile.");
      }
    }
    else if (Options.getCommonTokenAction()) {
      JavaCCErrors.warning("You have the COMMON_TOKEN_ACTION option set. " +
          "But you have not defined the method :\n" +
          "      void commonTokenAction(Token t)\n" +
          "in your SCANNER_DECLS. The generated scanner will not compile.");
    }
  }

  private void dumpDebugMethods(IndentingPrintWriter out) {
    out.println("   int kindCnt = 0;");
    out.println("  protected final String jjKindsForBitVector(int i, long vec) {");
    out.println("    String retVal = \"\";");
    out.println("    if (i == 0)");
    out.println("       kindCnt = 0;");
    out.println("    for (int j = 0; j < 64; j++) {");
    out.println("       if ((vec & (1L << j)) != 0L) {");
    out.println("          if (kindCnt++ > 0)");
    out.println("             retVal += \", \";");
    out.println("          if (kindCnt % 5 == 0)");
    out.println("             retVal += \"\\n     \";");
    out.println("          retVal += tokenImage[i * 64 + j];");
    out.println("       }");
    out.println("    }");
    out.println("    return retVal;");
    out.println("  }");
    out.println();

    out.println("  protected final String jjKindsForStateVector(" +
        "int lexState, int[] vec, int start, int end) {");
    out.println("    boolean[] kindDone = new boolean[" + maxOrdinal + "];");
    out.println("    String retVal = \"\";");
    out.println("    int cnt = 0;");
    out.println("    for (int i = start; i < end; i++) {");
    out.println("     if (vec[i] == -1)");
    out.println("       continue;");
    out.println("     int[] stateSet = statesForState[jjState][vec[i]];");
    out.println("     for (int j = 0; j < stateSet.length; j++) {");
    out.println("       int state = stateSet[j];");
    out.println("       if (!kindDone[kindForState[lexState][state]]) {");
    out.println("          kindDone[kindForState[lexState][state]] = true;");
    out.println("          if (cnt++ > 0)");
    out.println("             retVal += \", \";");
    out.println("          if (cnt % 5 == 0)");
    out.println("             retVal += \"\\n     \";");
    out.println("          retVal += tokenImage[kindForState[lexState][state]];");
    out.println("       }");
    out.println("     }");
    out.println("    }");
    out.println("    if (cnt == 0)");
    out.println("       return \"{  }\";");
    out.println("    else");
    out.println("       return \"{ \" + retVal + \" }\";");
    out.println("  }");
    out.println();
  }

  private void buildLexStatesTable() {
    String[] tmpLexStateName = new String[state.lexStateI2S.size()];
    for (TokenProduction tp : state.tokenProductions) {
      List<RegExpSpec> reSpecs = tp.reSpecs;
      List<TokenProduction> tps;

      for (int i = 0; i < tp.lexStates.length; i++) {
        if ((tps = allTpsForState.get(tp.lexStates[i])) == null) {
          tmpLexStateName[maxLexStates++] = tp.lexStates[i];
          allTpsForState.put(tp.lexStates[i], tps = new ArrayList<TokenProduction>());
        }

        tps.add(tp);
      }

      if (reSpecs == null || reSpecs.isEmpty()) {
        continue;
      }

      for (RegExpSpec reSpec : reSpecs) {
        if (maxOrdinal <= reSpec.regExp.ordinal) {
          maxOrdinal = reSpec.regExp.ordinal + 1;
        }
      }
    }

    kinds = new int[maxOrdinal];
    toSkip = new long[maxOrdinal / 64 + 1];
    toSpecial = new long[maxOrdinal / 64 + 1];
    toMore = new long[maxOrdinal / 64 + 1];
    toToken = new long[maxOrdinal / 64 + 1];
    toToken[0] = 1L;
    actions = new Action[maxOrdinal];
    actions[0] = state.eofAction;
    hasTokenActions = state.eofAction != null;
    initStates = new Hashtable<String, NfaState>();
    canMatchAnyChar = new int[maxLexStates];
    canLoop = new boolean[maxLexStates];
    stateHasActions = new boolean[maxLexStates];
    lexStateName = new String[maxLexStates];
    singlesToSkip = new NfaState[maxLexStates];
    System.arraycopy(tmpLexStateName, 0, lexStateName, 0, maxLexStates);

    for (int i = 0; i < maxLexStates; i++) {
      canMatchAnyChar[i] = -1;
    }

    hasNfa = new boolean[maxLexStates];
    mixed = new boolean[maxLexStates];
    maxLongsReqd = new int[maxLexStates];
    initMatch = new int[maxLexStates];
    newLexState = new String[maxOrdinal];
    newLexState[0] = state.eofNextState;
    hasEmptyMatch = false;
    lexStates = new int[maxOrdinal];
    ignoreCase = new boolean[maxOrdinal];
    rexprs = new RegularExpression[maxOrdinal];
    stringLiterals.allImages = new String[maxOrdinal];
    canReachOnMore = new boolean[maxLexStates];
  }

  private int getIndex(String name) {
    for (int i = 0; i < lexStateName.length; i++) {
      if (lexStateName[i] != null && lexStateName[i].equals(name)) {
        return i;
      }
    }
    throw new Error(); // Should never come here
  }

  public void addCharToSkip(char c, int kind) {
    singlesToSkip[lexStateIndex].addChar(c);
    singlesToSkip[lexStateIndex].kind = kind;
  }

  private void checkEmptyStringMatch() {
    int i, j, k, len;
    boolean[] seen = new boolean[maxLexStates];
    boolean[] done = new boolean[maxLexStates];
    String cycle;
    String reList;

    Outer:
    for (i = 0; i < maxLexStates; i++) {
      if (done[i] || initMatch[i] == 0 || initMatch[i] == Integer.MAX_VALUE ||
          canMatchAnyChar[i] != -1) {
        continue;
      }

      done[i] = true;
      len = 0;
      cycle = "";
      reList = "";

      for (k = 0; k < maxLexStates; k++) {
        seen[k] = false;
      }

      j = i;
      seen[i] = true;
      cycle += lexStateName[j] + "-->";
      while (newLexState[initMatch[j]] != null) {
        cycle += newLexState[initMatch[j]];
        if (seen[j = getIndex(newLexState[initMatch[j]])]) {
          break;
        }

        cycle += "-->";
        done[j] = true;
        seen[j] = true;
        if (initMatch[j] == 0 || initMatch[j] == Integer.MAX_VALUE ||
            canMatchAnyChar[j] != -1) {
          continue Outer;
        }
        if (len != 0) {
          reList += "; ";
        }
        reList += "line " + rexprs[initMatch[j]].getLine() + ", column " +
            rexprs[initMatch[j]].getColumn();
        len++;
      }

      if (newLexState[initMatch[j]] == null) {
        cycle += lexStateName[lexStates[initMatch[j]]];
      }

      for (k = 0; k < maxLexStates; k++) {
        canLoop[k] |= seen[k];
      }

      hasLoop = true;
      if (len == 0) {
        JavaCCErrors.warning(rexprs[initMatch[i]],
            "Regular expression" + ((rexprs[initMatch[i]].label.equals(""))
                ? "" : (" for " + rexprs[initMatch[i]].label)) +
                " can be matched by the empty string (\"\") in lexical state " +
                lexStateName[i] + ". This can result in an endless loop of " +
                "empty string matches.");
      }
      else {
        JavaCCErrors.warning(rexprs[initMatch[i]],
            "Regular expression" + ((rexprs[initMatch[i]].label.equals(""))
                ? "" : (" for " + rexprs[initMatch[i]].label)) +
                " can be matched by the empty string (\"\") in lexical state " +
                lexStateName[i] + ". This regular expression along with the " +
                "regular expressions at " + reList + " forms the cycle \n   " +
                cycle + "\ncontaining regular expressions with empty matches." +
                " This can result in an endless loop of empty string matches.");
      }
    }
  }

  private void dumpStaticVarDeclarations(IndentingPrintWriter out) {
    out.println();

    if (true) {
      out.println("/** Lexical state names. */");
      out.print("public static final String[] jjStateNames = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l1 = out.list(", ");
      for (int i = 0; i < maxLexStates; i++) {
        l1.item("\"" + lexStateName[i] + "\"");
      }
      out.println("};");
      out.unindent();
    }

    if (maxLexStates > 1) {
      out.println();
      out.println("/** Lex state array. */");
      out.print("private static final int[] jjNewLexState = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l2 = out.list(", ");
      for (int i = 0; i < maxOrdinal; i++) {
        if (newLexState[i] == null) {
          l2.item("-1");
        }
        else {
          l2.item(getIndex(newLexState[i]));
        }
      }
      out.println("};");
      out.unindent();
    }

    if (hasSkip || hasMore || hasSpecial) {
      // Bit vector for TOKEN
      out.print("private static final long[] jjToToken = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l3 = out.list(", ");
      for (int i = 0; i < maxOrdinal / 64 + 1; i++) {
        l3.item("0x" + Long.toHexString(toToken[i]) + "L");
      }
      out.println("};");
      out.unindent();
    }

    if (hasSkip || hasSpecial) {
      // Bit vector for SKIP
      out.print("private static final long[] jjToSkip = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l4 = out.list(", ");
      for (int i = 0; i < maxOrdinal / 64 + 1; i++) {
        l4.item("0x" + Long.toHexString(toSkip[i]) + "L");
      }
      out.println("};");
      out.unindent();
    }

    if (hasSpecial) {
      // Bit vector for SPECIAL
      out.print("private static final long[] jjToSpecial = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l5 = out.list(", ");
      for (int i = 0; i < maxOrdinal / 64 + 1; i++) {
        l5.item("0x" + Long.toHexString(toSpecial[i]) + "L");
      }
      out.println("};");
      out.unindent();
    }

    if (hasMore) {
      // Bit vector for MORE
      out.print("private static final long[] jjToMore = {");
      out.indent();
      IndentingPrintWriter.ListPrinter l6 = out.list(", ");
      for (int i = 0; i < maxOrdinal / 64 + 1; i++) {
        l6.item("0x" + Long.toHexString(toMore[i]) + "L");
      }
      out.println("};");
      out.unindent();
    }

    out.println("protected final CharStream charStream;");

    out.println("private final int[] jjRounds = " +
        "new int[" + stateSetSize + "];");
    out.println("private final int[] jjStateSet = " +
        "new int[" + (2 * stateSetSize) + "];");

    if (hasMoreActions || hasSkipActions || hasTokenActions) {
      if (keepImage) {
        // TODO get rid of thi field?
        out.println("private final StringBuilder jjImage = new StringBuilder();");
        out.println("private int jjImageLength;");
      }
    }

    out.println("protected int jjChar;");
    out.println("private char[] buffer = new char[256];");
    out.println("private int[] position = new int[buffer.length];");
    if (keepLineCol) {
      out.println("private int[] line = new int[buffer.length];");
      out.println("private int[] column = new int[buffer.length];");
    }
    out.println();
    out.println("private int offset, length;");
    out.println();

    out.println("protected int getBegin() {");
    out.indent();
    out.println("return position[0];");
    out.unindent();
    out.println("}");
    out.println();

    out.println("protected int getEnd() {");
    out.indent();
    out.println("return position[offset];");
    out.unindent();
    out.println("}");
    out.println();

    if (keepLineCol) {
      out.println("protected int getLine() {");
      out.indent();
      out.println("return line[0];");
      out.unindent();
      out.println("}");
      out.println();

      out.println("protected int getColumn() {");
      out.indent();
      out.println("return column[0];");
      out.unindent();
      out.println("}");
      out.println();
    }

    out.println("protected final void beginToken() {");
    out.indent();
    out.println("if (offset == length) {");
    out.indent();
    out.println("offset = length = 0;");
    out.println("buffer();");
    out.unindent();
    out.println("}");
    out.println("else {");
    out.indent();
    out.println("System.arraycopy(buffer, offset, buffer, 0, length - offset);");
    out.println("System.arraycopy(position, offset, position, 0, length - offset);");
    if (keepLineCol) {
      out.println("System.arraycopy(line, offset, line, 0, length - offset);");
      out.println("System.arraycopy(column, offset, column, 0, length - offset);");
    }
    out.println("length = length - offset;");
    out.println("offset = 0;");
    out.unindent();
    out.println("}");
    out.unindent();
    out.println("}");
    out.println();

    out.println("protected final int read() throws java.io.IOException {");
    out.indent();
    out.println("if (offset < length) {");
    out.indent();
    out.println("char c = buffer[offset++];");
    out.println("if (offset == length) {");
    out.indent();
    out.println("buffer();");
    out.unindent();
    out.println("}");
    out.println("return c;");
    out.unindent();
    out.println("}");
    out.println("int c = charStream.read();");
    out.println("if (c != -1) {");
    out.indent();
    out.println("ensureCapacity(offset);");
    out.println("buffer[offset] = (char) c;");
    out.unindent();
    out.println("}");
    out.println("length = ++offset;");
    out.println("buffer();");
    out.println("return c;");
    out.unindent();
    out.println("}");
    out.println();

    out.println("private void buffer() {");
    out.indent();
    out.println("ensureCapacity(offset);");
    out.println("position[offset] = charStream.position();");
    if (keepLineCol) {
      out.println("line[offset] = ((CharStream.LineColumnInfo) charStream).line();");
      out.println("column[offset] = ((CharStream.LineColumnInfo) charStream).column();");
    }
    out.unindent();
    out.println("}");
    out.println();

    out.println("private void ensureCapacity(int capacity) {");
    out.indent();
    out.println("if (capacity >= buffer.length) {");
    out.indent();
    out.println("buffer = java.util.Arrays.copyOf(buffer, capacity * 2);");
    out.println("position = java.util.Arrays.copyOf(position, capacity * 2);");
    if (keepLineCol) {
      out.println("line = java.util.Arrays.copyOf(line, capacity * 2);");
      out.println("column = java.util.Arrays.copyOf(column, capacity * 2);");
    }
    out.println("}\n");
    out.unindent();
    out.println("}");
    out.println();

    out.println("protected final void backup(int n) {");
    out.indent();
    out.println("if (n > offset) {");
    out.indent();
    out.println("throw new IllegalArgumentException();");
    out.unindent();
    out.println("}");
    out.println("offset -= n;");
    out.unindent();
    out.println("}");
    out.println();

    if (keepImage) {
      out.println("protected final String getImage() {");
      out.indent();
      if (hasEmptyMatch) {
        out.println("if (jjMatchedPos < 0) {");
        out.indent();
        out.println("return jjImage.toString();");
        out.unindent();
        out.println("}");
      }
      out.println("String literal = jjLiteralImages[jjMatchedKind];");
      out.println("if (literal != null) {");
      out.indent();
      out.println("return literal;");
      out.unindent();
      out.println("}");
      out.println("return new String(buffer, 0, offset);");
      out.unindent();
      out.println("}");
      out.println();

      if (hasMoreActions || hasSkipActions || hasTokenActions) {
        out.println("private void appendImage(int length) {");
        out.indent();
        out.println("jjImage.append(buffer, 0, length);");
        out.unindent();
        out.println("}");
        out.println();
      }
    }

    if (Options.getScannerUsesParser()) {
      out.println("public " + state.scannerClass() + "(" + state.parserClass() + " parser, CharStream charStream) {");
      out.indent();
      out.println("this.parser = parser;");
    }
    else {
      out.println("public " + state.scannerClass() + "(CharStream charStream) {");
      out.indent();
    }
    out.println("this.charStream = charStream;");
    out.unindent();
    out.println("}");
    out.println();

    if (Options.getScannerUsesParser()) {
      out.println("public " + state.scannerClass() + "(" + state.parserClass() + " parser, CharStream charStream, int state) {");
      out.indent();
      out.println("this(parser, charStream);");
    }
    else {
      out.println("public " + state.scannerClass() + "(CharStream charStream, int state) {");
      out.indent();
      out.println("this(charStream);");
    }
    out.println("switchTo(state);");
    out.unindent();
    out.println("}");
    out.println();

    out.println("private void reInitRounds() {");
    out.indent();
    out.println("jjRound = 0x" + Integer.toHexString(Integer.MIN_VALUE + 1) + ";");
    out.println("for (int i = 0; i < " + stateSetSize + "; i++) {");
    out.indent();
    out.println("jjRounds[i] = 0x" + Integer.toHexString(Integer.MIN_VALUE) + ";");
    out.unindent();
    out.println("}");
    out.unindent();
    out.println("}");
    out.println();

    out.println("/** Switch to specified lexical state. */");
    out.println("public void switchTo(int state) {");
    out.indent();
    out.println("if (state < 0 || state >= " + lexStateName.length + ") {");
    out.indent();
    out.println("throw new IllegalArgumentException(\"Invalid lexical state: \" + state + \"\");");
    out.unindent();
    out.println("}");
    out.println("jjState = state;");
    out.unindent();
    out.println("}");
    out.println();
  }

  private char maxChar(long l) {
    // Assumes l != 0L
    for (int i = 64; i-- > 0; ) {
      if ((l & (1L << i)) != 0L) {
        return (char) i;
      }
    }
    return 0xffff;
  }

  private void dumpMakeToken(IndentingPrintWriter out) {
    if (keepLineCol) {
      if (keepImage) {
        out.println("protected Token newToken(int kind, int begin, int end,");
        out.println("                         int line, int column, String image) {");
      }
      else {
        out.println("protected Token newToken(int kind, int begin, int end,");
        out.println("                         int line, int column) {");
      }
    }
    else {
      if (keepImage) {
        out.println("protected Token newToken(int kind, int begin, int end, String image) {");
      }
      else {
        out.println("protected Token newToken(int kind, int begin, int end) {");
      }
    }

    out.indent();

    if (keepImage) {
      if (Options.getTokenFactory().length() > 0) {
        out.println("Token token = " + Options.getTokenFactory() + ".newToken(kind, begin, end, image);");
      }
      else {
        out.println("Token token = Token.newToken(kind, begin, end, image);");
      }
    }
    else {
      if (Options.getTokenFactory().length() > 0) {
        out.println("Token token = " + Options.getTokenFactory() + ".newToken(kind, begin, end);");
      }
      else {
        out.println("Token token = Token.newToken(kind, begin, end);");
      }
    }

    if (keepLineCol) {
      if (hasEmptyMatch) {
        out.println("if (jjMatchedPos < 0) {");
        out.indent();
        out.println("token.setLineColumn(line, column);");
        out.unindent();
        out.println("}");
        out.println("else {");
        out.indent();
        out.println("token.setLineColumn(line, column);");
        out.unindent();
        out.println("}");
      }
      else {
        out.println("token.setLineColumn(line, column);");
      }
    }

    out.println("return token;");
    out.unindent();
    out.println("}");
    out.println();
  }

  private void dumpGetNextToken(IndentingPrintWriter out) {
    out.println("private int jjState = " + defaultLexState + ";");
    out.println("private int jjNewStateCount;");
    out.println("private int jjRound;");
    out.println("private int jjMatchedPos;");
    out.println("private int jjMatchedKind;");
    out.println();

    out.println("/** Get the next token that is not special. */");
    out.println("@Override public Token getNextToken() throws java.io.IOException {");
    out.indent();
    if (hasSpecial) {
      out.println("Token token = getAnyNextToken();")
          .println("Token specialToken = null;")
          .println("while (isSpecial(token.getKind())) {")
          .indent()
          .println("if (specialToken == null) {")
          .indent()
          .println("specialToken = token;")
          .unindent()
          .println("}")
          .println("else {")
          .indent()
          .println("token.specialToken = specialToken;")
          .println("specialToken = specialToken.next = token;")
          .unindent()
          .println("}")
          .println("token = getAnyNextToken();")
          .unindent()
          .println("}")
          .println("token.specialToken = specialToken;")
          .println("return token;");
    }
    else {
      out.println("return getAnyNextToken();");
    }
    out.unindent();
    out.println("}");
    out.println();

    out.println("/** Get the next normal or special, but not skip token. */");
    out.println("public Token getAnyNextToken() throws java.io.IOException {");
    out.indent();
    out.println("Token token;");
    out.println("int pos = 0;");
    out.println();
    out.println("loop:\nwhile (true) {");
    out.indent();
    out.println("beginToken();");
    out.println("jjChar = read();");
    out.println("if (jjChar == -1) {");
    out.indent();

    if (Options.getDebugScanner()) {
      out.println("debugPrinter.println(\"Returning the <EOF> token.\");");
    }

    out.println("jjMatchedKind = 0;");
    newTokenFragment(out);

    if (state.eofNextState != null || state.eofAction != null) {
      out.println("tokenLexicalActions(token);");
    }

    if (Options.getCommonTokenAction()) {
      out.println("commonTokenAction(token);");
    }

    out.println("return token;");
    out.unindent();
    out.println("}"); // if (jjChar == -1)

    if (hasMoreActions || hasSkipActions || hasTokenActions) {
      if (keepImage) {
        out.println("jjImage.setLength(0);");
        out.println("jjImageLength = 0;");
      }
    }

    out.println();

    if (hasMore) {
      out.println("while (true) {");
      out.indent();
    }

    // this also sets up the start state of the nfa
    if (maxLexStates > 1) {
      out.println("switch (jjState) {");
      out.indent();
    }

    for (int i = 0; i < maxLexStates; i++) {
      if (maxLexStates > 1) {
        out.println("case " + i + ":");
        out.indent();
      }

      if (singlesToSkip[i].hasTransitions()) {
        if (singlesToSkip[i].asciiMoves[0] != 0L &&
            singlesToSkip[i].asciiMoves[1] != 0L) {
          out.print("while ((jjChar < 64" + " && (0x" +
              Long.toHexString(singlesToSkip[i].asciiMoves[0]) +
              "L & (1L << jjChar)) != 0L) || (jjChar >> 6) == 1" +
              " && (0x" +
              Long.toHexString(singlesToSkip[i].asciiMoves[1]) +
              "L & (1L << (jjChar & 63))) != 0L)");
        }
        else if (singlesToSkip[i].asciiMoves[1] == 0L) {
          out.print("while (jjChar <= " +
              (int) maxChar(singlesToSkip[i].asciiMoves[0]) + " && (0x" +
              Long.toHexString(singlesToSkip[i].asciiMoves[0]) +
              "L & (1L << jjChar)) != 0L)");
        }
        else if (singlesToSkip[i].asciiMoves[0] == 0L) {
          out.print("while (jjChar > 63 && jjChar <= " +
              ((int) maxChar(singlesToSkip[i].asciiMoves[1]) + 64) +
              " && (0x" +
              Long.toHexString(singlesToSkip[i].asciiMoves[1]) +
              "L & (1L << (jjChar & 63))) != 0L)");
        }

        out.println(" {");
        out.indent();
        if (Options.getDebugScanner()) {
          out.println("debugPrinter.println(" +
              (maxLexStates > 1 ?
                  "\"<\" + jjStateNames[jjState] + \">\" + " : "") +
              "\"Skipping character : \" + " +
              "ScannerException.escape(String.valueOf(jjChar)) + \" (\" + jjChar + \")\");");
        }
        out.println("beginToken();");
        out.println("jjChar = read();");
        out.println("if (jjChar == -1) { continue loop; }");
        out.unindent();
        out.println("}");
      }

      if (initMatch[i] != Integer.MAX_VALUE && initMatch[i] != 0) {
        if (Options.getDebugScanner()) {
          out.println("debugPrinter.println(\"   Matched the empty string as \" + tokenImage[" +
              initMatch[i] + "] + \" token.\");");
        }

        out.println("jjMatchedKind = " + initMatch[i] + ";");
        out.println("jjMatchedPos = -1;");
        out.println("pos = 0;");
      }
      else {
        out.println("jjMatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        out.println("jjMatchedPos = 0;");
      }

      if (Options.getDebugScanner()) {
        out.println("debugPrinter.println(" +
            (maxLexStates > 1 ? "\"<\" + jjStateNames[jjState] + \">\" + " : "") +
            "\"Current character : \" + " +
            "ScannerException.escape(String.valueOf(jjChar)) + \" (\" + jjChar + \") " +
            "at line \" + getLine() + \" column \" + getColumn());");
      }

      out.println("pos = jjMoveStringLiteralDfa0_" + i + "();");

      if (canMatchAnyChar[i] != -1) {
        if (initMatch[i] != Integer.MAX_VALUE && initMatch[i] != 0) {
          out.print("if (jjMatchedPos < 0 || (jjMatchedPos == 0 && jjMatchedKind > " +
              canMatchAnyChar[i] + "))");
        }
        else {
          out.print("if (jjMatchedPos == 0 && jjMatchedKind > " +
              canMatchAnyChar[i] + ")");
        }
        out.println(" {");
        out.indent();

        if (Options.getDebugScanner()) {
          out.println("debugPrinter.println(\"   Current character matched as a \" + tokenImage[" +
              canMatchAnyChar[i] + "] + \" token.\");");
        }
        out.println("jjMatchedKind = " + canMatchAnyChar[i] + ";");

        if (initMatch[i] != Integer.MAX_VALUE && initMatch[i] != 0) {
          out.println("jjMatchedPos = 0;");
        }

        out.unindent();
        out.println("}");
      }

      if (maxLexStates > 1) {
        out.println("break;");
        out.unindent();
      }
    }

    if (maxLexStates > 1) {
      out.unindent();
      out.println("}");
    }
    else if (maxLexStates == 0) {
      out.println("jjMatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    if (maxLexStates > 1) {
      out.unindent();
    }

    out.indent();

    if (maxLexStates > 0) {
      out.println("if (jjMatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ") {");
      out.indent();
      out.print("if (jjMatchedPos + 1 < pos)");

      if (Options.getDebugScanner()) {
        out.println(" {");
        out.println("debugPrinter.println(" +
            "\"   Putting back \" + (pos - jjMatchedPos - 1) + \" characters into the input stream.\");");
      }

      out.println("backup(pos - jjMatchedPos - 1);");

      if (Options.getDebugScanner()) {
        out.println("}");
      }

      if (Options.getDebugScanner()) {
        out.println("debugPrinter.println(" +
            "\"****** FOUND A \" + tokenImage[jjMatchedKind] + \" MATCH " +
            "(\" + ScannerException.escape(new String(buffer, 0, jjMatchedPos + 1))) + " +
            "\") ******\\n\");");
      }

      if (hasSkip || hasMore || hasSpecial) {
        out.println("if (isToken(jjMatchedKind)) {");
        out.indent();
      }

      newTokenFragment(out);

      if (hasTokenActions) {
        out.println("tokenLexicalActions(token);");
      }

      if (maxLexStates > 1) {
        out.println("if (jjNewLexState[jjMatchedKind] != -1)")
            .indent()
            .println("jjState = jjNewLexState[jjMatchedKind];")
            .unindent();
      }

      if (Options.getCommonTokenAction()) {
        out.println("commonTokenAction(token);");
      }

      out.println("return token;");

      if (hasSkip || hasMore || hasSpecial) {
        out.unindent();
        out.println("}");

        if (hasSkip || hasSpecial) {
          if (hasMore) {
            out.print("else if (isSkip(jjMatchedKind))");
          }
          else {
            out.print("else");
          }

          out.println(" {");
          out.indent();

          if (hasSpecial) {
            out.println("if (isSpecial(jjMatchedKind)) {");
            out.indent();
            newTokenFragment(out);

            if (hasSkipActions) {
              out.println("skipLexicalActions(token);");
            }

            if (maxLexStates > 1) {
              out.println("if (jjNewLexState[jjMatchedKind] != -1)")
                  .indent()
                  .println("jjState = jjNewLexState[jjMatchedKind];")
                  .unindent();
            }

            out.println("return token;");
            out.unindent();
            out.println("}");

            if (hasSkipActions) {
              out.println("skipLexicalActions(null);");
            }
          }
          else if (hasSkipActions) {
            out.println("skipLexicalActions(null);");
          }

          if (maxLexStates > 1) {
            out.println("if (jjNewLexState[jjMatchedKind] != -1)")
                .indent()
                .println("jjState = jjNewLexState[jjMatchedKind];")
                .unindent();
          }

          out.println("continue loop;");

          out.unindent();
          out.println("}");
        }

        if (hasMore) {
          if (hasMoreActions) {
            out.println("moreLexicalActions();");
          }
          else if (hasSkipActions || hasTokenActions) {
            if (keepImage) {
              out.println("jjImageLength += jjMatchedPos + 1;");
            }
          }

          if (maxLexStates > 1) {
            out.println("if (jjNewLexState[jjMatchedKind] != -1)")
                .indent()
                .println("jjState = jjNewLexState[jjMatchedKind];")
                .unindent();
          }
          out.println("pos = 0;");
          out.println("jjMatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

          out.println("jjChar = read();");
          out.println("if (jjChar != -1) {");
          out.indent();
          if (Options.getDebugScanner()) {
            out.println("debugPrinter.println(" +
                (maxLexStates > 1 ? "\"<\" + jjStateNames[jjState] + \">\" + " : "") +
                "\"Current character : \" + " +
                "ScannerException.escape(String.valueOf(jjChar)) + \" (\" + jjChar + \") " +
                "at line \" + getLine() + \" column \" + getColumn());");
          }
          out.println("continue;");
          out.unindent();
          out.println("}");
        }
      }
      out.unindent();
      out.println("}");

      if (keepLineCol) {
        out.println("reportError(jjState, position[offset - 1], line[offset - 1], column[offset - 1], jjChar);");
      }
      else {
        out.println("reportError(jjState, position[offset - 1], jjChar);");
      }
    }

    if (hasMore) {
      out.unindent();
      out.println("}");
    }

    out.unindent();
    out.println("}");

    out.unindent();
    out.println("}");
    out.println();

    if (hasMore || hasSkip || hasSpecial) {
      out.println("/**")
          .println(" * Verify whether the specified is a normal token kind.")
          .println(" *")
          .println(" * @param kind A token kind.")
          .println(" * @return <code>true</code> if a normal token, </code>false</code> otherwise.")
          .println(" */")
          .println("public static boolean isToken(int kind) {")
          .indent()
          .println("return (jjToToken[kind >> 6] & (1L << (kind & 63))) != 0L;")
          .unindent()
          .println("}")
          .println();
    }

    if (hasSkip || hasSpecial) {
      out.println("/**")
          .println(" * Verify whether to ignore the specified token.")
          .println(" *")
          .println(" * @param kind A token kind.")
          .println(" * @return <code>true</code> if ignore token, </code>false</code> otherwise.")
          .println(" */")
          .println("public static boolean isSkip(int kind) {")
          .indent()
          .println("return (jjToSkip[kind >> 6] & (1L << (kind & 63))) != 0L;")
          .unindent()
          .println("}")
          .println();
    }

    if (hasSpecial) {
      out.println("/**")
          .println(" * Verify whether to ignore the specified special token.")
          .println(" *")
          .println(" * @param kind A token kind.")
          .println(" * @return <code>true</code> if ignore special token, </code>false</code> otherwise.")
          .println(" */")
          .println("public static boolean isSpecial(int kind) {")
          .indent()
          .println("return (jjToSpecial[kind >> 6] & (1L << (kind & 63))) != 0L;")
          .unindent()
          .println("}")
          .println();
    }

    if (keepLineCol) {
      out.println("protected void reportError(int state, int pos, int line, int column, int character) {");
    }
    else {
      out.println("protected void reportError(int state, int pos, int character) {");
    }
    out.indent();
    if (keepLineCol) {
      out.println("throw new ScannerException(state, ScannerException.LEXICAL_ERROR,");
      out.println("  pos, line, column, character);");
    }
    else {
      out.println("throw new ScannerException(state, ScannerException.LEXICAL_ERROR,");
      out.println("  pos, character);");
    }
    out.unindent();
    out.println("}");
    out.println();
  }

  private void newTokenFragment(IndentingPrintWriter out) {
    if (keepLineCol) {
      if (keepImage) {
        out.println("token = newToken(jjMatchedKind, getBegin(), getEnd(),");
        out.println("    getLine(), getColumn(), getImage());");
      }
      else {
        out.println("token = newToken(jjMatchedKind, getBegin(), getEnd(),");
        out.println("    getLine(), getColumn());");
      }
    }
    else {
      if (keepImage) {
        out.println("token = newToken(jjMatchedKind, getBegin(), getEnd(), getImage());");
      }
      else {
        out.println("token = newToken(jjMatchedKind, getBegin(), getEnd());");
      }
    }
  }

  public void dumpSkipActions(IndentingPrintWriter out)
      throws IOException {
    out.println("void skipLexicalActions(Token matchedToken) {");
    out.indent();
    out.println("switch(jjMatchedKind) {");
    out.indent();

    outer:
    for (int i = 0; i < maxOrdinal; i++) {
      if ((toSkip[i / 64] & (1L << (i % 64))) == 0L) {
        continue;
      }

      while (true) {
        Action act = actions[i];
        if ((act == null
            || act.getActionTokens() == null
            || act.getActionTokens().size() == 0)
            && !canLoop[lexStates[i]]) {
          continue outer;
        }

        out.println("case " + i + " :");
        out.indent();

        if (initMatch[lexStates[i]] == i && canLoop[lexStates[i]]) {
          out.println("if (jjMatchedPos == -1) {");
          out.indent();
          out.println("if (jjBeenHere[" + lexStates[i] + "]");
          out.println("  && jjEmptyLineNo[" + lexStates[i] + "] == getLine()");
          out.println("  && jjEmptyColumnNo[" + lexStates[i] + "] == getColumn())");
          out.indent();
          out.println("throw new ScannerException("
              + "(\"Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + getLine() + \", "
              + "column \" + getColumn() + \".\"), ScannerException.LOOP_DETECTED);");
          out.unindent();
          out.println("jjEmptyLineNo[" + lexStates[i] + "] = getLine();");
          out.println("jjEmptyColumnNo[" + lexStates[i] + "] = getColumn();");
          out.println("jjBeenHere[" + lexStates[i] + "] = true;");
          out.unindent();
          out.println("}");
        }

        if ((act = actions[i]) == null
            || act.getActionTokens().size() == 0) {
          break;
        }

        if (keepImage) {
          if (stringLiterals.allImages[i] != null) {
            out.println("jjImage.append(jjLiteralImages[" + i + "]);");
          }
          else {
            out.println("appendImage(jjImageLength + jjMatchedPos + 1);");
          }
        }

        TokenPrinter tp = new TokenPrinter();

        tp.setup(act.getActionTokens().get(0));
        tp.column = 1;

        for (int j = 0; j < act.getActionTokens().size(); j++) {
          tp.printToken(act.getActionTokens().get(j), out);
        }
        out.println();

        break;
      }

      out.println("break;");
      out.unindent();
    }

    out.println("default:")
        .indent()
        .print("break;")
        .unindent();
    out.unindent();
    out.println("}");
    out.unindent();
    out.println("}");
    out.println();
  }

  public void dumpMoreActions(IndentingPrintWriter out)
      throws IOException {
    out.println("void moreLexicalActions() {");
    out.indent();
    if (keepImage) {
      out.println("jjImageLength += jjMatchedPos + 1;");
    }
    out.println("switch(jjMatchedKind) {");
    out.indent();

    outer:
    for (int i = 0; i < maxOrdinal; i++) {
      if ((toMore[i / 64] & (1L << (i % 64))) == 0L) {
        continue;
      }

      while (true) {
        Action act = actions[i];
        if ((act == null
            || act.getActionTokens() == null
            || act.getActionTokens().size() == 0)
            && !canLoop[lexStates[i]]) {
          continue outer;
        }

        out.println("case " + i + " :");
        out.indent();

        if (initMatch[lexStates[i]] == i && canLoop[lexStates[i]]) {
          out.println("if (jjMatchedPos == -1) {");
          out.indent();
          out.println("if (jjBeenHere[" + lexStates[i] + "]");
          out.println("  && jjEmptyLineNo[" + lexStates[i] + "] == getLine()");
          out.println("  && jjEmptyColumnNo[" + lexStates[i] + "] == getColumn())");
          out.indent();
          out.println("throw new ScannerException("
              + "(\"Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + getLine() + \", "
              + "column \" + getColumn() + \".\"), ScannerException.LOOP_DETECTED);");
          out.unindent();
          out.println("jjEmptyLineNo[" + lexStates[i] + "] = getLine();");
          out.println("jjEmptyColumnNo[" + lexStates[i] + "] = getColumn();");
          out.println("jjBeenHere[" + lexStates[i] + "] = true;");
          out.unindent();
          out.println("}");
        }

        if ((act = actions[i]) == null
            || act.getActionTokens().size() == 0) {
          break;
        }

        if (keepImage) {
          if (stringLiterals.allImages[i] != null) {
            out.println("jjImage.append(jjLiteralImages[" + i + "]);");
          }
          else {
            out.println("appendImage(jjImageLength);");
          }
          out.println("jjImageLength = 0;");
        }

        TokenPrinter tp = new TokenPrinter();

        tp.setup(act.getActionTokens().get(0));
        tp.column = 1;

        for (int j = 0; j < act.getActionTokens().size(); j++) {
          tp.printToken(act.getActionTokens().get(j), out);
        }
        out.println();

        break;
      }

      out.println("break;");
      out.unindent();
    }

    out.println("default:")
        .indent()
        .print("break;")
        .unindent();
    out.unindent();
    out.println("}");
    out.unindent();
    out.println("}");
    out.println();
  }

  public void dumpTokenActions(IndentingPrintWriter out)
      throws IOException {
    out.println("void tokenLexicalActions(Token matchedToken) {");
    out.indent();
    out.println("switch(jjMatchedKind) {");
    out.indent();

    outer:
    for (int i = 0; i < maxOrdinal; i++) {
      if ((toToken[i / 64] & (1L << (i % 64))) == 0L) {
        continue;
      }

      while (true) {
        Action act = actions[i];
        if ((act == null
            || act.getActionTokens() == null
            || act.getActionTokens().size() == 0)
            && !canLoop[lexStates[i]]) {
          continue outer;
        }

        out.println("case " + i + " :");
        out.indent();

        if (initMatch[lexStates[i]] == i && canLoop[lexStates[i]]) {
          out.println("if (jjMatchedPos == -1) {");
          out.indent();
          out.println("if (jjBeenHere[" + lexStates[i] + "]");
          out.println("  && jjEmptyLineNo[" + lexStates[i] + "] == getLine()");
          out.println("  && jjEmptyColumnNo[" + lexStates[i] + "] == getColumn())");
          out.indent();
          out.println("throw new ScannerException("
              + "(\"Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + getLine() + \", "
              + "column \" + getColumn() + \".\"), ScannerException.LOOP_DETECTED);");
          out.unindent();
          out.println("jjEmptyLineNo[" + lexStates[i] + "] = getLine();");
          out.println("jjEmptyColumnNo[" + lexStates[i] + "] = getColumn();");
          out.println("jjBeenHere[" + lexStates[i] + "] = true;");
          out.unindent();
          out.println("}");
        }

        if ((act = actions[i]) == null
            || act.getActionTokens().size() == 0) {
          break;
        }

        if (keepImage) {
          if (i == 0) {
            out.println("jjImage.setLength(0);"); // For EOF no image is there
          }
          else {
            out.print("jjImage.append");
            if (stringLiterals.allImages[i] != null) {
              out.println("(jjLiteralImages[" + i + "]);");
            }
            else {
              if (stringLiterals.allImages[i] != null) {
                out.println("jjImage.append(jjLiteralImages[" + i + "]);");
              }
              else {
                out.println("appendImage(jjImageLength + jjMatchedPos + 1);");
              }
            }
          }
        }

        TokenPrinter tp = new TokenPrinter();

        tp.setup(act.getActionTokens().get(0));
        tp.column = 1;

        for (int j = 0; j < act.getActionTokens().size(); j++) {
          tp.printToken(act.getActionTokens().get(j), out);
        }
        out.println();

        break;
      }

      out.println("break;");
      out.unindent();
    }

    out.println("default:")
        .indent()
        .print("break;")
        .unindent();
    out.unindent();
    out.println("}");
    out.unindent();
    out.println("}");
    out.println();
  }

  boolean isRegExp(int i) {
    int n = i / 64;
    long mask = 1L << (i % 64);
    return ((toSkip[n] & mask) == 0 && (toMore[n] & mask) == 0 && (toToken[n] & mask) == 0)
        || (toSkip[n] & mask) != 0 || (toMore[n] & mask) != 0 || canReachOnMore[lexStates[i]];
  }
}
