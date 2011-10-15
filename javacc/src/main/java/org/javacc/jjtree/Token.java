/* Generated By:JavaCC: Do not edit this line. Token.java Version 4.1 */
/**
 * This file contains the code for JavaCCParser generated
 * by JavaCCParser itself.
 */

package org.javacc.jjtree;

/** Describes the input token stream. */
public class Token {
  private int kind;
  private int beginOffset;
  private int endOffset;
  private int beginLine;
  private int beginColumn;
  private int endLine;
  private int endColumn;
  private String image;
  /**
   * A reference to the next regular (non-special) token from the input
   * stream.  If this is the last token from the input stream, or if the
   * token manager has not read tokens beyond this one, this field is
   * set to null.  This is true only if this token is also a regular
   * token.  Otherwise, see below for a description of the contents of
   * this field.
   */
  public Token next;
  /**
   * This field is used to access special tokens that occur prior to this
   * token, but after the immediately preceding regular (non-special) token.
   * If there are no such special tokens, this field is set to null.
   * When there are more than one such special token, this field refers
   * to the last of these special tokens, which in turn refers to the next
   * previous special token through its specialToken field, and so on
   * until the first special token (whose specialToken field is null).
   * The next fields of special tokens refer to other special tokens that
   * immediately follow it (without an intervening regular token).  If there
   * is no such token, this field is null.
   */
  public Token specialToken;

  /** No-argument constructor */
  public Token() {}

  /** Constructs a new token for the specified Image. */
  public Token(int kind) {
    this(kind, null);
  }

  /** Constructs a new token for the specified Image and Kind. */
  public Token(int kind, String image) {
    this.kind = kind;
    this.image = image;
  }

  /**
   * Gets an integer that describes the kind of this token.
   *
   * This numbering system is determined by JavaCCParser, and a table of these numbers is
   * stored in the file ...Constants.java.
   *
   * @return Token kind.
   */
  public int getKind() {
    return kind;
  }

  /**
   * Change token kind.
   *
   * @param kind New token kind.
   */
  public void setKind(int kind) {
    this.kind = kind;
  }

  /**
   * Gets text matched by this token.
   *
   * @return Token image.
   */
  public String getImage() {
    return image;
  }

  /**
   * Change token image.
   *
   * @param image New token image.
   */
  public void setImage(String image) {
    this.image = image;
  }

  /** @return Index of the first character of the token, inclusive. */
  public int getBeginOffset() {
    return beginOffset;
  }

  /** @return Index of the last character of the token, exclusive. */
  public int getEndOffset() {
    return endOffset;
  }

  /**
   * Set index of first and last token character.
   *
   * @param begin Index of the first character of the token, inclusive.
   * @param end   Index of the last character of the token, exclusive.
   */
  public void setOffset(int begin, int end) {
    beginOffset = begin;
    endOffset = end;
  }

  /** @return The line number of the first character of this token. */
  public int getBeginLine() {
    return beginLine;
  }

  /** @return The column number of the first character of this token. */
  public int getBeginColumn() {
    return beginColumn;
  }

  /** @return The line number of the last character of this token. */
  public int getEndLine() {
    return endLine;
  }

  /** @return The column number of the last character of this token. */
  public int getEndColumn() {
    return endColumn;
  }

  /**
   * Set token line and column numbers.
   *
   * @param beginLine   The line number of the first character of this token
   * @param beginColumn The column number of the first character of this token.
   * @param endLine     The line number of the last character of this token.
   * @param endColumn   The column number of the last character of this token.
   */
  public void setLineColumn(int beginLine, int beginColumn, int endLine, int endColumn) {
    this.beginLine = beginLine;
    this.beginColumn = beginColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  /** Returns the image. */
  public String toString() {
    return image;
  }

  /**
   * Returns a new Token object, by default. However, if you want, you
   * can create and return subclass objects based on the value of ofKind.
   * Simply add the cases to the switch for all those special cases.
   * For example, if you have a subclass of Token called IDToken that
   * you want to create if ofKind is ID, simply add something like :
   *
   * case MyParserConstants.ID : return new IDToken(ofKind, image);
   *
   * to the following switch statement. Then you can cast matchedToken
   * variable to the appropriate type and use it in your lexical actions.
   *
   * @param ofKind Token kind.
   * @param image  Token image.
   * @return New token instance.
   */
  public static Token newToken(int ofKind, String image) {
    switch (ofKind) {
      default:
        return new Token(ofKind, image);
      case JJTreeConstants.RUNSIGNEDSHIFT:
      case JJTreeConstants.RSIGNEDSHIFT:
      case JJTreeConstants.GT:
        return new GTToken(ofKind, image);
    }
  }

  public static Token newToken(int ofKind) {
    return newToken(ofKind, null);
  }

  /** Greater than Token. */
  public static class GTToken extends Token {
    public GTToken(int kind, String image) {
      super(kind, image);
    }

    int realKind = JJTreeConstants.GT;
  }
}
