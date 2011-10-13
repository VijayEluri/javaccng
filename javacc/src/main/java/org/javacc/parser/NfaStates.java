package org.javacc.parser;

import org.javacc.utils.io.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

final class NfaStates {
  public boolean unicodeWarningGiven = false;
  public int generatedStates = 0;
  public int idCnt = 0;
  public int lohiByteCnt;
  public int dummyStateIndex = -1;
  public boolean done;
  public boolean[] mark;
  public boolean[] stateDone;
  public List allStates = new ArrayList();
  public List indexedAllStates = new ArrayList();
  public List nonAsciiTableForMethod = new ArrayList();
  public Hashtable equivStatesTable = new Hashtable();
  public Hashtable allNextStates = new Hashtable();
  public Hashtable lohiByteTab = new Hashtable();
  public Hashtable stateNameForComposite = new Hashtable();
  public Hashtable compositeStateTable = new Hashtable();
  public Hashtable stateBlockTable = new Hashtable();
  public Hashtable stateSetsToFix = new Hashtable();
  public boolean jjCheckNAddStatesUnaryNeeded = false;
  public boolean jjCheckNAddStatesDualNeeded = false;
  public List allBitVectors = new ArrayList();
  public int[] tmpIndices = new int[512]; // 2 * 256
  public String allBits = "{\n   0xffffffffffffffffL, " +
      "0xffffffffffffffffL, " +
      "0xffffffffffffffffL, " +
      "0xffffffffffffffffL\n};";
  public Hashtable tableToDump = new Hashtable();
  public List orderedStateSet = new ArrayList();
  public int lastIndex = 0;
  public int[][] kinds;
  public int[][][] statesForState;

  void reInit() {
    generatedStates = 0;
    idCnt = 0;
    dummyStateIndex = -1;
    done = false;
    mark = null;
    stateDone = null;

    allStates.clear();
    indexedAllStates.clear();
    equivStatesTable.clear();
    allNextStates.clear();
    compositeStateTable.clear();
    stateBlockTable.clear();
    stateNameForComposite.clear();
    stateSetsToFix.clear();
  }

  public void computeClosures() {
    for (int i = allStates.size(); i-- > 0; ) {
      NfaState tmp = (NfaState) allStates.get(i);

      if (!tmp.closureDone) { tmp.optimizeEpsilonMoves(true); }
    }

    for (int i = 0; i < allStates.size(); i++) {
      NfaState tmp = (NfaState) allStates.get(i);

      if (!tmp.closureDone) { tmp.optimizeEpsilonMoves(false); }
    }

    for (int i = 0; i < allStates.size(); i++) {
      NfaState tmp = (NfaState) allStates.get(i);
      tmp.epsilonMoveArray = new NfaState[tmp.epsilonMoves.size()];
      tmp.epsilonMoves.copyInto(tmp.epsilonMoveArray);
    }
  }

  public boolean canStartNfaUsingAscii(LexGen lexGen, char c) {
    if (c >= 128) { throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu"); }

    String s = lexGen.initialState.GetEpsilonMovesString();

    if (s == null || s.equals("null;")) { return false; }

    int[] states = (int[]) allNextStates.get(s);

    for (int i = 0; i < states.length; i++) {
      NfaState tmp = (NfaState) indexedAllStates.get(states[i]);

      if ((tmp.asciiMoves[c / 64] & (1L << c % 64)) != 0L) { return true; }
    }

    return false;
  }

  public int moveFromSet(char c, List states, List newStates) {
    int tmp;
    int retVal = Integer.MAX_VALUE;

    for (int i = states.size(); i-- > 0; ) {
      if (retVal >
          (tmp = ((NfaState) states.get(i)).moveFrom(c, newStates))) { retVal = tmp; }
    }

    return retVal;
  }

  public int moveFromSetForRegEx(char c, NfaState[] states, NfaState[] newStates, int round) {
    int start = 0;
    int sz = states.length;

    for (int i = 0; i < sz; i++) {
      NfaState tmp1, tmp2;

      if ((tmp1 = states[i]) == null) { break; }

      if (tmp1.canMoveUsingChar(c)) {
        if (tmp1.kindToPrint != Integer.MAX_VALUE) {
          newStates[start] = null;
          return 1;
        }

        NfaState[] v = tmp1.next.epsilonMoveArray;
        for (int j = v.length; j-- > 0; ) {
          if ((tmp2 = v[j]).round != round) {
            tmp2.round = round;
            newStates[start++] = tmp2;
          }
        }
      }
    }

    newStates[start] = null;
    return Integer.MAX_VALUE;
  }

  boolean equalLoByteVectors(List vec1, List vec2) {
    if (vec1 == null || vec2 == null) { return false; }

    if (vec1 == vec2) { return true; }

    if (vec1.size() != vec2.size()) { return false; }

    for (int i = 0; i < vec1.size(); i++) {
      if (((Integer) vec1.get(i)).intValue() !=
          ((Integer) vec2.get(i)).intValue()) { return false; }
    }

    return true;
  }

  boolean equalNonAsciiMoveIndices(int[] moves1, int[] moves2) {
    if (moves1 == moves2) { return true; }

    if (moves1 == null || moves2 == null) { return false; }

    if (moves1.length != moves2.length) { return false; }

    for (int i = 0; i < moves1.length; i++) {
      if (moves1[i] != moves2[i]) { return false; }
    }

    return true;
  }

  int addCompositeStateSet(String stateSetString, boolean starts) {
    Integer stateNameToReturn;

    if ((stateNameToReturn = (Integer) stateNameForComposite.get(stateSetString)) != null) {
      return stateNameToReturn.intValue();
    }

    int toRet = 0;
    int[] nameSet = (int[]) allNextStates.get(stateSetString);

    if (!starts) { stateBlockTable.put(stateSetString, stateSetString); }

    if (nameSet == null) {
      throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu; nameSet null for : " +
          stateSetString);
    }

    if (nameSet.length == 1) {
      stateNameToReturn = new Integer(nameSet[0]);
      stateNameForComposite.put(stateSetString, stateNameToReturn);
      return nameSet[0];
    }

    for (int i = 0; i < nameSet.length; i++) {
      if (nameSet[i] == -1) { continue; }

      NfaState st = (NfaState) indexedAllStates.get(nameSet[i]);
      st.isComposite = true;
      st.compositeStates = nameSet;
    }

    while (toRet < nameSet.length &&
        (starts && ((NfaState) indexedAllStates.get(nameSet[toRet])).inNextOf > 1)) { toRet++; }

    Enumeration e = compositeStateTable.keys();
    String s;
    while (e.hasMoreElements()) {
      s = (String) e.nextElement();
      if (!s.equals(stateSetString) && entersect(stateSetString, s)) {
        int[] other = (int[]) compositeStateTable.get(s);

        while (toRet < nameSet.length &&
            ((starts && ((NfaState) indexedAllStates.get(nameSet[toRet])).inNextOf > 1) ||
                elemOccurs(nameSet[toRet], other) >= 0)) { toRet++; }
      }
    }

    int tmp;

    if (toRet >= nameSet.length) {
      if (dummyStateIndex == -1) { tmp = dummyStateIndex = generatedStates; }
      else { tmp = ++dummyStateIndex; }
    }
    else { tmp = nameSet[toRet]; }

    stateNameToReturn = new Integer(tmp);
    stateNameForComposite.put(stateSetString, stateNameToReturn);
    compositeStateTable.put(stateSetString, nameSet);

    return tmp;
  }

  int stateNameForComposite(String stateSetString) {
    return ((Integer) stateNameForComposite.get(stateSetString)).intValue();
  }

  int[] getStateSetIndicesForUse(String arrayString) {
    int[] ret;
    int[] set = (int[]) allNextStates.get(arrayString);

    if ((ret = (int[]) tableToDump.get(arrayString)) == null) {
      ret = new int[2];
      ret[0] = lastIndex;
      ret[1] = lastIndex + set.length - 1;
      lastIndex += set.length;
      tableToDump.put(arrayString, ret);
      orderedStateSet.add(set);
    }

    return ret;
  }

  public void DumpStateSets(IndentingPrintWriter ostr) {
    int cnt = 0;

    ostr.print("static final int[] jjNextStates = {");
    for (int i = 0; i < orderedStateSet.size(); i++) {
      int[] set = (int[]) orderedStateSet.get(i);

      for (int j = 0; j < set.length; j++) {
        if (cnt++ % 16 == 0) { ostr.print("\n   "); }

        ostr.print(set[j] + ", ");
      }
    }

    ostr.println("\n};");
  }

  String getStateSetString(int[] states) {
    String retVal = "{ ";
    for (int i = 0; i < states.length; ) {
      retVal += states[i] + ", ";

      if (i++ > 0 && i % 16 == 0) { retVal += "\n"; }
    }

    retVal += "};";
    allNextStates.put(retVal, states);
    return retVal;
  }

  String getStateSetString(List states) {
    if (states == null || states.size() == 0) { return "null;"; }

    int[] set = new int[states.size()];
    String retVal = "{ ";
    for (int i = 0; i < states.size(); ) {
      int k;
      retVal += (k = ((NfaState) states.get(i)).stateName) + ", ";
      set[i] = k;

      if (i++ > 0 && i % 16 == 0) { retVal += "\n"; }
    }

    retVal += "};";
    allNextStates.put(retVal, set);
    return retVal;
  }

  int numberOfBitsSet(long l) {
    int ret = 0;
    for (int i = 0; i < 63; i++) { if (((l >> i) & 1L) != 0L) { ret++; } }

    return ret;
  }

  int onlyOneBitSet(long l) {
    int oneSeen = -1;
    for (int i = 0; i < 64; i++) {
      if (((l >> i) & 1L) != 0L) {
        if (oneSeen >= 0) { return -1; }
        oneSeen = i;
      }
    }

    return oneSeen;
  }

  int elemOccurs(int elem, int[] arr) {
    for (int i = arr.length; i-- > 0; ) { if (arr[i] == elem) { return i; } }

    return -1;
  }

  boolean entersect(String set1, String set2) {
    if (set1 == null || set2 == null) { return false; }

    int[] nameSet1 = (int[]) allNextStates.get(set1);
    int[] nameSet2 = (int[]) allNextStates.get(set2);

    if (nameSet1 == null || nameSet2 == null) { return false; }

    if (nameSet1 == nameSet2) { return true; }

    for (int i = nameSet1.length; i-- > 0; ) {
      for (int j = nameSet2.length; j-- > 0; ) { if (nameSet1[i] == nameSet2[j]) { return true; } }
    }

    return false;
  }

  void dumpHeadForCase(IndentingPrintWriter ostr, int byteNum) {
    if (byteNum == 0) { ostr.println("         long l = 1L << jjChar;"); }
    else if (byteNum == 1) { ostr.println("         long l = 1L << (jjChar & 077);"); }

    else {
      if (Options.getJavaUnicodeEscape() || unicodeWarningGiven) {
        ostr.println("         int hiByte = (int)(jjChar >> 8);");
        ostr.println("         int i1 = hiByte >> 6;");
        ostr.println("         long l1 = 1L << (hiByte & 077);");
      }

      ostr.println("         int i2 = (jjChar & 0xff) >> 6;");
      ostr.println("         long l2 = 1L << (jjChar & 077);");
    }

    //ostr.println("         MatchLoop: do");
    ostr.println("         do");
    ostr.println("         {");

    ostr.println("            switch(jjStateSet[--i])");
    ostr.println("            {");
  }

  Vector partitionStatesSetForAscii(int[] states, int byteNum) {
    int[] cardinalities = new int[states.length];
    Vector original = new Vector();
    Vector partition = new Vector();
    NfaState tmp;

    original.setSize(states.length);
    int cnt = 0;
    for (int i = 0; i < states.length; i++) {
      tmp = (NfaState) allStates.get(states[i]);

      if (tmp.asciiMoves[byteNum] != 0L) {
        int j;
        int p = numberOfBitsSet(tmp.asciiMoves[byteNum]);

        for (j = 0; j < i; j++) { if (cardinalities[j] <= p) { break; } }

        for (int k = i; k > j; k--) { cardinalities[k] = cardinalities[k - 1]; }

        cardinalities[j] = p;

        original.insertElementAt(tmp, j);
        cnt++;
      }
    }

    original.setSize(cnt);

    while (original.size() > 0) {
      tmp = (NfaState) original.get(0);
      original.removeElement(tmp);

      long bitVec = tmp.asciiMoves[byteNum];
      List subSet = new ArrayList();
      subSet.add(tmp);

      for (int j = 0; j < original.size(); j++) {
        NfaState tmp1 = (NfaState) original.get(j);

        if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
          bitVec |= tmp1.asciiMoves[byteNum];
          subSet.add(tmp1);
          original.removeElementAt(j--);
        }
      }

      partition.add(subSet);
    }

    return partition;
  }

  void dumpCompositeStatesAsciiMoves(IndentingPrintWriter ostr,
                                     String key, int byteNum, boolean[] dumped) {
    int i;

    int[] nameSet = (int[]) allNextStates.get(key);

    if (nameSet.length == 1 || dumped[stateNameForComposite(key)]) { return; }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    String toPrint = "";
    boolean stateBlock = (stateBlockTable.get(key) != null);

    for (i = 0; i < nameSet.length; i++) {
      tmp = (NfaState) allStates.get(nameSet[i]);

      if (tmp.asciiMoves[byteNum] != 0L) {
        if (neededStates++ == 1) { break; }
        else { toBePrinted = tmp; }
      }
      else { dumped[tmp.stateName] = true; }

      if (tmp.stateForCase != null) {
        if (stateForCase != null) { throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : "); }

        stateForCase = tmp.stateForCase;
      }
    }

    if (stateForCase != null) { toPrint = stateForCase.printNoBreak(ostr, byteNum, dumped); }

    if (neededStates == 0) {
      if (stateForCase != null && toPrint.equals("")) { ostr.println("                  break;"); }
      return;
    }

    if (neededStates == 1) {
      //if (byteNum == 1)
      //System.out.println(toBePrinted.stateName + " is the only state for "
      //+ key + " ; and key is : " + stateNameForComposite(key));

      if (!toPrint.equals("")) { ostr.print(toPrint); }

      ostr.println("               case " + stateNameForComposite(key) + ":");

      if (!dumped[toBePrinted.stateName] && !stateBlock && toBePrinted.inNextOf > 1) {
        ostr.println("               case " + toBePrinted.stateName + ":");
      }

      dumped[toBePrinted.stateName] = true;
      toBePrinted.dumpAsciiMove(ostr, byteNum, dumped);
      return;
    }

    List partition = partitionStatesSetForAscii(nameSet, byteNum);

    if (!toPrint.equals("")) { ostr.print(toPrint); }

    int keyState = stateNameForComposite(key);
    ostr.println("               case " + keyState + ":");
    if (keyState < generatedStates) { dumped[keyState] = true; }

    for (i = 0; i < partition.size(); i++) {
      List subSet = (List) partition.get(i);

      for (int j = 0; j < subSet.size(); j++) {
        tmp = (NfaState) subSet.get(j);

        if (stateBlock) { dumped[tmp.stateName] = true; }
        tmp.dumpAsciiMoveForCompositeState(ostr, byteNum, j != 0);
      }
    }

    if (stateBlock) { ostr.println("                  break;"); }
    else { ostr.println("                  break;"); }
  }

  void dumpAsciiMoves(LexGen lexGen, IndentingPrintWriter ostr, int byteNum) {
    boolean[] dumped = new boolean[Math.max(generatedStates, dummyStateIndex + 1)];
    Enumeration e = compositeStateTable.keys();

    dumpHeadForCase(ostr, byteNum);

    while (e.hasMoreElements()) { dumpCompositeStatesAsciiMoves(ostr, (String) e.nextElement(), byteNum, dumped); }

    for (int i = 0; i < allStates.size(); i++) {
      NfaState temp = (NfaState) allStates.get(i);

      if (dumped[temp.stateName] || temp.lexState != lexGen.lexStateIndex ||
          !temp.hasTransitions() || temp.dummy ||
          temp.stateName == -1) { continue; }

      String toPrint = "";

      if (temp.stateForCase != null) {
        if (temp.inNextOf == 1) { continue; }

        if (dumped[temp.stateForCase.stateName]) { continue; }

        toPrint = (temp.stateForCase.printNoBreak(ostr, byteNum, dumped));

        if (temp.asciiMoves[byteNum] == 0L) {
          if (toPrint.equals("")) { ostr.println("                  break;"); }

          continue;
        }
      }

      if (temp.asciiMoves[byteNum] == 0L) { continue; }

      if (!toPrint.equals("")) { ostr.print(toPrint); }

      dumped[temp.stateName] = true;
      ostr.println("               case " + temp.stateName + ":");
      temp.dumpAsciiMove(ostr, byteNum, dumped);
    }

    ostr.println("               default: break;");
    ostr.println("            }");
    ostr.println("         } while(i != startsAt);");
  }

  void dumpCompositeStatesNonAsciiMoves(IndentingPrintWriter ostr,
                                        String key, boolean[] dumped) {
    int i;
    int[] nameSet = (int[]) allNextStates.get(key);

    if (nameSet.length == 1 || dumped[stateNameForComposite(key)]) { return; }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    String toPrint = "";
    boolean stateBlock = (stateBlockTable.get(key) != null);

    for (i = 0; i < nameSet.length; i++) {
      tmp = (NfaState) allStates.get(nameSet[i]);

      if (tmp.nonAsciiMethod != -1) {
        if (neededStates++ == 1) { break; }
        else { toBePrinted = tmp; }
      }
      else { dumped[tmp.stateName] = true; }

      if (tmp.stateForCase != null) {
        if (stateForCase != null) { throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : "); }

        stateForCase = tmp.stateForCase;
      }
    }

    if (stateForCase != null) { toPrint = stateForCase.printNoBreak(ostr, -1, dumped); }

    if (neededStates == 0) {
      if (stateForCase != null && toPrint.equals("")) { ostr.println("                  break;"); }

      return;
    }

    if (neededStates == 1) {
      if (!toPrint.equals("")) { ostr.print(toPrint); }

      ostr.println("               case " + stateNameForComposite(key) + ":");

      if (!dumped[toBePrinted.stateName] && !stateBlock && toBePrinted.inNextOf > 1) {
        ostr.println("               case " + toBePrinted.stateName + ":");
      }

      dumped[toBePrinted.stateName] = true;
      toBePrinted.dumpNonAsciiMove(ostr, dumped);
      return;
    }

    if (!toPrint.equals("")) { ostr.print(toPrint); }

    int keyState = stateNameForComposite(key);
    ostr.println("               case " + keyState + ":");
    if (keyState < generatedStates) { dumped[keyState] = true; }

    for (i = 0; i < nameSet.length; i++) {
      tmp = (NfaState) allStates.get(nameSet[i]);

      if (tmp.nonAsciiMethod != -1) {
        if (stateBlock) { dumped[tmp.stateName] = true; }
        tmp.dumpNonAsciiMoveForCompositeState(ostr);
      }
    }

    if (stateBlock) { ostr.println("                  break;"); }
    else { ostr.println("                  break;"); }
  }

  public void dumpCharAndRangeMoves(LexGen lexGen, IndentingPrintWriter ostr) {
    boolean[] dumped = new boolean[Math.max(generatedStates, dummyStateIndex + 1)];
    Enumeration e = compositeStateTable.keys();
    int i;

    dumpHeadForCase(ostr, -1);

    while (e.hasMoreElements()) { dumpCompositeStatesNonAsciiMoves(ostr, (String) e.nextElement(), dumped); }

    for (i = 0; i < allStates.size(); i++) {
      NfaState temp = (NfaState) allStates.get(i);

      if (temp.stateName == -1 || dumped[temp.stateName] || temp.lexState != lexGen.lexStateIndex ||
          !temp.hasTransitions() || temp.dummy) { continue; }

      String toPrint = "";

      if (temp.stateForCase != null) {
        if (temp.inNextOf == 1) { continue; }

        if (dumped[temp.stateForCase.stateName]) { continue; }

        toPrint = (temp.stateForCase.printNoBreak(ostr, -1, dumped));

        if (temp.nonAsciiMethod == -1) {
          if (toPrint.equals("")) { ostr.println("                  break;"); }

          continue;
        }
      }

      if (temp.nonAsciiMethod == -1) { continue; }

      if (!toPrint.equals("")) { ostr.print(toPrint); }

      dumped[temp.stateName] = true;
      //System.out.println("case : " + temp.stateName);
      ostr.println("               case " + temp.stateName + ":");
      temp.dumpNonAsciiMove(ostr, dumped);
    }

    ostr.println("               default: break;");
    ostr.println("            }");
    ostr.println("         } while(i != startsAt);");
  }

  public void dumpNonAsciiMoveMethods(IndentingPrintWriter ostr) {
    if (!Options.getJavaUnicodeEscape() && !unicodeWarningGiven) { return; }

    if (nonAsciiTableForMethod.size() <= 0) { return; }

    for (int i = 0; i < nonAsciiTableForMethod.size(); i++) {
      NfaState tmp = (NfaState) nonAsciiTableForMethod.get(i);
      tmp.dumpNonAsciiMoveMethod(ostr);
    }
  }

  void reArrange() {
    List v = allStates;
    allStates = new ArrayList(Collections.nCopies(generatedStates, null));

    if (allStates.size() != generatedStates) { throw new Error("What??"); }

    for (int j = 0; j < v.size(); j++) {
      NfaState tmp = (NfaState) v.get(j);
      if (tmp.stateName != -1 && !tmp.dummy) { allStates.set(tmp.stateName, tmp); }
    }
  }

  void printBoilerPlate(IndentingPrintWriter ostr) {
    ostr.println();
    ostr.println("private void " +
        "jjCheckNAdd(int state)");
    ostr.println("{");
    ostr.println("   if (jjRounds[state] != jjRound)");
    ostr.println("   {");
    ostr.println("      jjStateSet[jjNewStateCount++] = state;");
    ostr.println("      jjRounds[state] = jjRound;");
    ostr.println("   }");
    ostr.println("}");

    ostr.println();
    ostr.println("private void " +
        "jjAddStates(int start, int end)");
    ostr.println("{");
    ostr.println("   do {");
    ostr.println("      jjStateSet[jjNewStateCount++] = jjNextStates[start];");
    ostr.println("   } while (start++ != end);");
    ostr.println("}");

    ostr.println();
    ostr.println("private void " +
        "jjCheckNAddTwoStates(int state1, int state2)");
    ostr.println("{");
    ostr.println("   jjCheckNAdd(state1);");
    ostr.println("   jjCheckNAdd(state2);");
    ostr.println("}");
    if (jjCheckNAddStatesDualNeeded) {
      ostr.println();
      ostr.println("private void " +
          "jjCheckNAddStates(int start, int end)");
      ostr.println("{");
      ostr.println("   do {");
      ostr.println("      jjCheckNAdd(jjNextStates[start]);");
      ostr.println("   } while (start++ != end);");
      ostr.println("}");
      ostr.println("");
    }

    if (jjCheckNAddStatesUnaryNeeded) {
      ostr.println();
      ostr.println("private void " +
          "jjCheckNAddStates(int start)");
      ostr.println("{");
      ostr.println("   jjCheckNAdd(jjNextStates[start]);");
      ostr.println("   jjCheckNAdd(jjNextStates[start + 1]);");
      ostr.println("}");
      ostr.println("");
    }
  }

  void findStatesWithNoBreak() {
    Hashtable printed = new Hashtable();
    boolean[] put = new boolean[generatedStates];
    int cnt = 0;
    int i, j, foundAt = 0;

    Outer:
    for (j = 0; j < allStates.size(); j++) {
      NfaState stateForCase = null;
      NfaState tmpState = (NfaState) allStates.get(j);

      if (tmpState.stateName == -1 || tmpState.dummy || !tmpState.usefulState() ||
          tmpState.next == null || tmpState.next.usefulEpsilonMoves < 1) { continue; }

      String s = tmpState.next.epsilonMovesString;

      if (compositeStateTable.get(s) != null || printed.get(s) != null) { continue; }

      printed.put(s, s);
      int[] nexts = (int[]) allNextStates.get(s);

      if (nexts.length == 1) { continue; }

      int state = cnt;
      //System.out.println("State " + tmpState.stateName + " : " + s);
      for (i = 0; i < nexts.length; i++) {
        if ((state = nexts[i]) == -1) { continue; }

        NfaState tmp = (NfaState) allStates.get(state);

        if (!tmp.isComposite && tmp.inNextOf == 1) {
          if (put[state]) { throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu"); }

          foundAt = i;
          cnt++;
          stateForCase = tmp;
          put[state] = true;

          //System.out.print(state + " : " + tmp.inNextOf + ", ");
          break;
        }
      }
      //System.out.println("");

      if (stateForCase == null) { continue; }

      for (i = 0; i < nexts.length; i++) {
        if ((state = nexts[i]) == -1) { continue; }

        NfaState tmp = (NfaState) allStates.get(state);

        if (!put[state] && tmp.inNextOf > 1 && !tmp.isComposite && tmp.stateForCase == null) {
          cnt++;
          nexts[i] = -1;
          put[state] = true;

          int toSwap = nexts[0];
          nexts[0] = nexts[foundAt];
          nexts[foundAt] = toSwap;

          tmp.stateForCase = stateForCase;
          stateForCase.stateForCase = tmp;
          stateSetsToFix.put(s, nexts);

          //System.out.println("For : " + s + "; " + stateForCase.stateName +
          //" and " + tmp.stateName);

          continue Outer;
        }
      }

      for (i = 0; i < nexts.length; i++) {
        if ((state = nexts[i]) == -1) { continue; }

        NfaState tmp = (NfaState) allStates.get(state);
        if (tmp.inNextOf <= 1) { put[state] = false; }
      }
    }
  }

  public void dumpMoveNfa(LexGen lexGen, IndentingPrintWriter ostr) {
    //if (!boilerPlateDumped)
    //   printBoilerPlate(ostr);

    //boilerPlateDumped = true;
    int i;
    int[] kindsForStates = null;

    if (kinds == null) {
      kinds = new int[lexGen.maxLexStates][];
      statesForState = new int[lexGen.maxLexStates][][];
    }

    reArrange();

    for (i = 0; i < allStates.size(); i++) {
      NfaState temp = (NfaState) allStates.get(i);

      if (temp.lexState != lexGen.lexStateIndex ||
          !temp.hasTransitions() || temp.dummy ||
          temp.stateName == -1) { continue; }

      if (kindsForStates == null) {
        kindsForStates = new int[generatedStates];
        statesForState[lexGen.lexStateIndex] = new int[Math.max(generatedStates, dummyStateIndex + 1)][];
      }

      kindsForStates[temp.stateName] = temp.lookingFor;
      statesForState[lexGen.lexStateIndex][temp.stateName] = temp.compositeStates;

      temp.generateNonAsciiMoves(ostr);
    }

    Enumeration e = stateNameForComposite.keys();

    while (e.hasMoreElements()) {
      String s = (String) e.nextElement();
      int state = ((Integer) stateNameForComposite.get(s)).intValue();

      if (state >= generatedStates) { statesForState[lexGen.lexStateIndex][state] = (int[]) allNextStates.get(s); }
    }

    if (stateSetsToFix.size() != 0) { fixStateSets(); }

    kinds[lexGen.lexStateIndex] = kindsForStates;

    ostr.println("private int " +
        "jjMoveNfa" + lexGen.lexStateSuffix + "(int startState, int curPos) throws java.io.IOException");
    ostr.println("{");

    if (generatedStates == 0) {
      ostr.println("   return curPos;");
      ostr.println("}");
      return;
    }

    if (lexGen.mixed[lexGen.lexStateIndex]) {
      ostr.println("   int strKind = jjMatchedKind;");
      ostr.println("   int strPos = jjMatchedPos;");
      ostr.println("   int seenUpto;");
      ostr.println("   charStream.backup(seenUpto = curPos + 1);");
      ostr.println("   jjChar = charStream.readChar();");
      ostr.println("   if (jjChar == -1) { throw new Error(\"Internal Error\"); }");
      ostr.println("   curPos = 0;");
    }

    ostr.println("   int startsAt = 0;");
    ostr.println("   jjNewStateCount = " + generatedStates + ";");
    ostr.println("   int i = 1;");
    ostr.println("   jjStateSet[0] = startState;");

    if (Options.getDebugTokenManager()) {
      ostr.println("      debugPrinter.println(\"   Starting NFA to match one of : \" + " +
          "jjKindsForStateVector(jjLexState, jjStateSet, 0, 1));");
    }

    if (Options.getDebugTokenManager()) {
      ostr.println("      debugPrinter.println(" + (lexGen.maxLexStates > 1 ?
          "\"<\" + jjLexStateNames[jjLexState] + \">\" + " :
          "") + "\"Current character : \" + " +
          "TokenManagerError.escape(String.valueOf(jjChar)) + \" (\" + jjChar + \") " +
          "at line \" + charStream.getEndLine() + \" column \" + charStream.getEndColumn());");
    }

    ostr.println("   int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    ostr.println("   for (;;)");
    ostr.println("   {");
    ostr.println("      if (++jjRound == 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    ostr.println("         ReInitRounds();");
    ostr.println("      if (jjChar < 64)");
    ostr.println("      {");

    dumpAsciiMoves(lexGen, ostr, 0);

    ostr.println("      }");

    ostr.println("      else if (jjChar < 128)");

    ostr.println("      {");

    dumpAsciiMoves(lexGen, ostr, 1);

    ostr.println("      }");

    ostr.println("      else");
    ostr.println("      {");

    dumpCharAndRangeMoves(lexGen, ostr);

    ostr.println("      }");

    ostr.println("      if (kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    ostr.println("      {");
    ostr.println("         jjMatchedKind = kind;");
    ostr.println("         jjMatchedPos = curPos;");
    ostr.println("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    ostr.println("      }");
    ostr.println("      ++curPos;");

    if (Options.getDebugTokenManager()) {
      ostr.println("      if (jjMatchedKind != 0 && jjMatchedKind != 0x" +
          Integer.toHexString(Integer.MAX_VALUE) + ")");
      ostr.println("         debugPrinter.println(" +
          "\"   Currently matched the first \" + (jjMatchedPos + 1) + \" characters as" +
          " a \" + tokenImage[jjMatchedKind] + \" token.\");");
    }

    ostr.println("      if ((i = jjNewStateCount) == (startsAt = " +
        generatedStates + " - (jjNewStateCount = startsAt)))");
    if (lexGen.mixed[lexGen.lexStateIndex]) { ostr.println("         break;"); }
    else { ostr.println("         return curPos;"); }

    if (Options.getDebugTokenManager()) {
      ostr.println("      debugPrinter.println(\"   Possible kinds of longer matches : \" + " +
          "jjKindsForStateVector(jjLexState, jjStateSet, startsAt, i));");
    }

    ostr.println("      jjChar = charStream.readChar();");

    if (lexGen.mixed[lexGen.lexStateIndex]) { ostr.println("      if (jjChar == -1) { break; }"); }
    else { ostr.println("      if (jjChar == -1) { return curPos; }"); }

    if (Options.getDebugTokenManager()) {
      ostr.println("      debugPrinter.println(" + (lexGen.maxLexStates > 1 ?
          "\"<\" + jjLexStateNames[jjLexState] + \">\" + " :
          "") + "\"Current character : \" + " +
          "TokenManagerError.escape(String.valueOf(jjChar)) + \" (\" + jjChar + \") " +
          "at line \" + charStream.getEndLine() + \" column \" + charStream.getEndColumn());");
    }

    ostr.println("   }");

    if (lexGen.mixed[lexGen.lexStateIndex]) {
      ostr.println("   if (jjMatchedPos > strPos)");
      ostr.println("      return curPos;");
      ostr.println("");
      ostr.println("   int toRet = Math.max(curPos, seenUpto);");
      ostr.println("");
      ostr.println("   if (curPos < toRet)");
      ostr.println("      for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )");
      ostr.println("         jjChar = charStream.readChar();");
      ostr.println("         if (jjChar == -1) { " +
          "throw new Error(\"Internal Error : Please send a bug report.\"); }");
      ostr.println("");
      ostr.println("   if (jjMatchedPos < strPos)");
      ostr.println("   {");
      ostr.println("      jjMatchedKind = strKind;");
      ostr.println("      jjMatchedPos = strPos;");
      ostr.println("   }");
      ostr.println("   else if (jjMatchedPos == strPos && jjMatchedKind > strKind)");
      ostr.println("      jjMatchedKind = strKind;");
      ostr.println("");
      ostr.println("   return toRet;");
    }

    ostr.println("}");
    allStates.clear();
  }

  public void dumpStatesForState(IndentingPrintWriter ostr) {
    ostr.print("protected static final int[][][] statesForState = ");

    if (statesForState == null) {
      ostr.println("null;");
      return;
    }
    else { ostr.println("{"); }

    for (int i = 0; i < statesForState.length; i++) {

      if (statesForState[i] == null) {
        ostr.println(" null,");
        continue;
      }

      ostr.println(" {");

      for (int j = 0; j < statesForState[i].length; j++) {
        int[] stateSet = statesForState[i][j];

        if (stateSet == null) {
          ostr.println("   { " + j + " },");
          continue;
        }

        ostr.print("   { ");

        for (int k = 0; k < stateSet.length; k++) { ostr.print(stateSet[k] + ", "); }

        ostr.println("},");
      }
      ostr.println(" },");
    }
    ostr.println("\n};");
  }

  public void dumpStatesForKind(IndentingPrintWriter ostr) {
    dumpStatesForState(ostr);
    boolean moreThanOne = false;
    int cnt = 0;

    ostr.print("protected static final int[][] kindForState = ");

    if (kinds == null) {
      ostr.println("null;");
      return;
    }
    else { ostr.println("{"); }

    for (int i = 0; i < kinds.length; i++) {
      if (moreThanOne) { ostr.println(","); }
      moreThanOne = true;

      if (kinds[i] == null) { ostr.println("null"); }
      else {
        cnt = 0;
        ostr.print("{ ");
        for (int j = 0; j < kinds[i].length; j++) {
          if (cnt++ > 0) { ostr.print(","); }

          if (cnt % 15 == 0) { ostr.print("\n  "); }
          else if (cnt > 1) { ostr.print(" "); }

          ostr.print(kinds[i][j]);
        }
        ostr.print("}");
      }
    }
    ostr.println("\n};");
  }

  void insertInOrder(List v, NfaState s) {
    int j;

    for (j = 0; j < v.size(); j++) {
      if (((NfaState) v.get(j)).id > s.id) { break; }
      else if (((NfaState) v.get(j)).id == s.id) { return; }
    }

    v.add(j, s);
  }

  char[] expandCharArr(char[] oldArr, int incr) {
    char[] ret = new char[oldArr.length + incr];
    System.arraycopy(oldArr, 0, ret, 0, oldArr.length);
    return ret;
  }

  boolean equalCharArr(char[] arr1, char[] arr2) {
    if (arr1 == arr2) { return true; }

    if (arr1 != null &&
        arr2 != null &&
        arr1.length == arr2.length) {
      for (int i = arr1.length; i-- > 0; ) { if (arr1[i] != arr2[i]) { return false; } }

      return true;
    }

    return false;
  }

  boolean allBitsSet(String bitVec) {
    return bitVec.equals(allBits);
  }

  int addStartStateSet(String stateSetString) {
    return addCompositeStateSet(stateSetString, true);
  }

  int initStateName(LexGen lexGen) {
    String s = lexGen.initialState.GetEpsilonMovesString();

    if (lexGen.initialState.usefulEpsilonMoves != 0) { return stateNameForComposite(s); }
    return -1;
  }

  void fixStateSets() {
    Hashtable fixedSets = new Hashtable();
    Enumeration e = stateSetsToFix.keys();
    int[] tmp = new int[generatedStates];
    int i;

    while (e.hasMoreElements()) {
      String s;
      int[] toFix = (int[]) stateSetsToFix.get(s = (String) e.nextElement());
      int cnt = 0;

      //System.out.print("Fixing : ");
      for (i = 0; i < toFix.length; i++) {
        //System.out.print(toFix[i] + ", ");
        if (toFix[i] != -1) { tmp[cnt++] = toFix[i]; }
      }

      int[] fixed = new int[cnt];
      System.arraycopy(tmp, 0, fixed, 0, cnt);
      fixedSets.put(s, fixed);
      allNextStates.put(s, fixed);
      //System.out.println(" as " + getStateSetString(fixed));
    }

    for (i = 0; i < allStates.size(); i++) {
      NfaState tmpState = (NfaState) allStates.get(i);
      int[] newSet;

      if (tmpState.next == null || tmpState.next.usefulEpsilonMoves == 0) { continue; }

      /*if (compositeStateTable.get(tmpState.next.epsilonMovesString) != null)
         tmpState.next.usefulEpsilonMoves = 1;
      else*/
      if ((newSet = (int[]) fixedSets.get(tmpState.next.epsilonMovesString)) != null) {
        tmpState.fixNextStates(newSet);
      }
    }
  }
}