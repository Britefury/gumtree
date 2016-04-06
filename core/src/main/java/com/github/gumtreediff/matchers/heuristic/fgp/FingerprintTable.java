package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.HashMap;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FingerprintTable {
    private HashMap<String, Integer> shaToIndex = new HashMap<>();


    public int size() {
        return shaToIndex.size();
    }


    int getIndexForSha(String sha) {
        Integer index = shaToIndex.get(sha);
        if (index == null) {
            index = shaToIndex.size();
            shaToIndex.put(sha, index);
        }
        return index;
    }
}
