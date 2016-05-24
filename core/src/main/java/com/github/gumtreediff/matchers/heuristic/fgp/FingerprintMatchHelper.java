package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;

/**
 * Created by Geoff on 07/04/2016.
 */
public class FingerprintMatchHelper {
    public FGPNode.NodeMapping mappingA, mappingB;
    public FGPNode fgpTreeA, fgpTreeB;
    public FeatureVectorTable fv;

    public FingerprintMatchHelper(ITree a, ITree b) {
        mappingA = new FGPNode.NodeMapping();
        mappingB = new FGPNode.NodeMapping();
        fgpTreeA = new FGPNode(a, mappingA);
        fgpTreeB = new FGPNode(b, mappingB);

        fgpTreeA.initDistFromRoot();
        fgpTreeB.initDistFromRoot();

        fv = new FeatureVectorTable();
        fv.addTree(fgpTreeA);
        fv.addTree(fgpTreeB);
    }


    public double scoreMatchContext(ITree a, ITree b) {
        return FeatureVectorTable.scoreMatchContext(mappingA.get(a), mappingB.get(b));
    }

    public double scoreMatch(ITree a, ITree b) {
        return FeatureVectorTable.scoreMatch(mappingA.get(a), mappingB.get(b), null, null, null);
    }

    public double scoreMatch(ITree a, ITree b, MappingStore mappings) {
        return FeatureVectorTable.scoreMatch(mappingA.get(a), mappingB.get(b), mappingA, mappingB, mappings);
    }

    public void logMatch(ITree a, ITree b) {
        FeatureVectorTable.logMatch(mappingA.get(a), mappingB.get(b), null, null, null);
    }

    public void logMatch(ITree a, ITree b, MappingStore mappings) {
        FeatureVectorTable.logMatch(mappingA.get(a), mappingB.get(b), mappingA, mappingB, mappings);
    }
}
