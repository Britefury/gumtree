package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.tree.ITree;

/**
 * Created by Geoff on 07/04/2016.
 */
public class FingerprintMatchHelper {
    public FGPNode.NodeMapping mappingA, mappingB;
    public FGPNode fgbTreeA, fgpTreeB;
    public FeatureVectorTable fv;

    public FingerprintMatchHelper(ITree a, ITree b) {
        mappingA = new FGPNode.NodeMapping();
        mappingB = new FGPNode.NodeMapping();
        fgbTreeA = new FGPNode(a, mappingA);
        fgpTreeB = new FGPNode(b, mappingB);

        fv = new FeatureVectorTable();
        fv.addTree(fgbTreeA);
        fv.addTree(fgpTreeB);
    }


    public double scoreMatchContext(ITree a, ITree b) {
        return FeatureVectorTable.scoreMatchContext(mappingA.get(a), mappingB.get(b));
    }

    public double scoreMatch(ITree a, ITree b) {
        return FeatureVectorTable.scoreMatch(mappingA.get(a), mappingB.get(b));
    }
}
