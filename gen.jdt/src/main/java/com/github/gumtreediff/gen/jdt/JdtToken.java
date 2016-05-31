package com.github.gumtreediff.gen.jdt;

/**
 * Created by Geoff on 27/05/2016.
 */
public class JdtToken {
    private int tokenType;
    private char src[];
    private int start, end;

    public JdtToken(int tokenType, char src[], int start, int end) {
        this.tokenType = tokenType;
        this.src = src;
        this.start = start;
        this.end = end;
    }

    public int getTokenType() {
        return tokenType;
    }

    public char[] getSrc() {
        return src;
    }

    public String getSrcString() {
        return new String(src);
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
