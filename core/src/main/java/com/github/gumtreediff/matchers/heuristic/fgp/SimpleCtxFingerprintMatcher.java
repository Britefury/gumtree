package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.tree.ITree;

/**
 * Created by Geoff on 17/05/2016.
 */
@Register(id = "fg")
public class SimpleCtxFingerprintMatcher extends AbstractSimpleCtxFingerprintMatcher {
    public SimpleCtxFingerprintMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    @Override
    public void match() {
        long t1 = System.nanoTime();

        FingerprintMatchHelper matchHelper = new FingerprintMatchHelper(src, dst);

        int nTA = matchHelper.fgpTreeA.subtreeSize;
        int nTB = matchHelper.fgpTreeB.subtreeSize;

        long t2 = System.nanoTime();
        double fgTime = (t2 - t1) * 1.0e-9;

        topDownMatch(matchHelper.fgpTreeA, matchHelper.fgpTreeB, 0);

//        int nTopDown = mappings.asSet().size();

        long t3 = System.nanoTime();
        double topDownTime = (t3 - t2) * 1.0e-9;

        bottomUpMatch(matchHelper, null, matchHelper.fgpTreeA, matchHelper.fgpTreeB);

        long t4 = System.nanoTime();
        double bottomUpTime = (t4 - t3) * 1.0e-9;

//        System.err.println("Fingerprint generation " + nTA + " x " + nTB + " nodes: " + fgTime + "s, top down " + topDownTime + "s, bottom up " + bottomUpTime + "s");
    }
}
