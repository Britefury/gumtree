package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVectorTable {
    FingerprintTable shapeFingerprints = new FingerprintTable();
    FingerprintTable contentFingerprints = new FingerprintTable();
    ArrayList<FeatureVector> featsByContentFGIndex = new ArrayList<>();


    public void addTree(FGPNode tree) {
        tree.updateFingerprintIndex(shapeFingerprints, contentFingerprints);

        for (int i = featsByContentFGIndex.size(); i < contentFingerprints.size(); i++) {
            featsByContentFGIndex.add(null);
        }

        buildTreeFeaturesBottomUp(tree);
//        buildNodeFeaturesTopDown(tree, new FeatureVector(), new FeatureVector());
        buildNodeFeaturesTopDown(tree, 0.0, 0.0);
    }

    private void buildTreeFeaturesBottomUp(FGPNode root) {
        root.leftSiblingsFeats = new FeatureVector();
        root.rightSiblingsFeats = new FeatureVector();
        buildNodeFeaturesBottomUp(root);
    }

    private void buildNodeFeaturesBottomUp(FGPNode node) {
        // Update child feature vectors
        for (FGPNode child: node.children) {
            buildNodeFeaturesBottomUp(child);
        }

        // Compute cumulative features of children and set
        if (node.children.length > 0) {
            FeatureVector cumulativeChildFeats[] = new FeatureVector[node.children.length + 1];
            cumulativeChildFeats[0] = new FeatureVector();

            for (int i = 0; i < node.children.length; i++) {
                cumulativeChildFeats[i+1] = cumulativeChildFeats[i].add(node.children[i].nodeFeatures);
            }

            FeatureVector last = cumulativeChildFeats[cumulativeChildFeats.length-1];

            for (int i = 0; i < node.children.length; i++) {
                FGPNode child = node.children[i];
                child.leftSiblingsFeats = cumulativeChildFeats[i];
                child.rightSiblingsFeats = last.sub(cumulativeChildFeats[i+1]);
            }
        }

        // Compute node feature vectors
        int contentFg = node.getContentFingerprintIndex();
        FeatureVector feats = featsByContentFGIndex.get(contentFg);
        if (feats == null) {
            feats = new FeatureVector();
//            int shapeFG = node.getShapeFingerprintIndex();
            feats.set(contentFg, 1);

            for (FGPNode child: node.children) {
                feats = feats.add(child.nodeFeatures);
            }
            featsByContentFGIndex.set(contentFg, feats);
        }

        node.nodeFeatures = feats;
    }

    private void buildNodeFeaturesTopDown(FGPNode node, double leftTree, double rightTree) {
        node.leftTree = leftTree;
        node.rightTree = rightTree;

        for (FGPNode child: node.children) {
            buildNodeFeaturesTopDown(child, leftTree + child.leftSiblingsFeats.getSum(),
                                     rightTree + child.rightSiblingsFeats.getSum());
        }
    }
//
//    private void buildNodeFeaturesTopDown(FGPNode node, FeatureVector fgLeft, FeatureVector fgRight) {
//        node.leftTreeFeats = fgLeft;
//        node.rightTreeFeats = fgRight;
//
//        for (FGPNode child: node.children) {
//            buildNodeFeaturesTopDown(child, fgLeft.add(child.leftSiblingsFeats), fgRight.add(child.rightSiblingsFeats));
//        }
//    }


    public static double scoreMatchContext(FGPNode a, FGPNode b) {
        double leftSim = Math.min(a.leftTree, b.leftTree) / (Math.max(a.leftTree, b.leftTree) + 1.0e-9);
        double rightSim = Math.min(a.rightTree, b.rightTree) / (Math.max(a.rightTree, b.rightTree) + 1.0e-9);
        return leftSim * 0.5 + rightSim * 0.5 +
                a.leftSiblingsFeats.jaccardSimilarity(b.leftSiblingsFeats) * 5.0 +
                a.rightSiblingsFeats.jaccardSimilarity(b.rightSiblingsFeats) * 5.0;
    }

//    public static double scoreMatchContext(FGPNode a, FGPNode b) {
//        return a.leftTreeFeats.jaccardSimilarity(b.leftTreeFeats) +
//                a.rightTreeFeats.jaccardSimilarity(b.rightTreeFeats) +
//                a.leftSiblingsFeats.jaccardSimilarity(b.leftSiblingsFeats) * 10.0 +
//                a.rightSiblingsFeats.jaccardSimilarity(b.rightSiblingsFeats) * 10.0;
//    }
//
    public static double scoreMatch(FGPNode a, FGPNode b, FGPNode.NodeMapping mappingsA, FGPNode.NodeMapping mappingsB,
                                    MappingStore mappings) {
        double jaccard;
        if (mappingsA == null && mappingsB == null && mappings == null) {
            jaccard = a.nodeFeatures.jaccardSimilarity(b.nodeFeatures);
        }
        else {
            double jaccardParts[] = a.nodeFeatures.jaccardSimilarityParts(b.nodeFeatures);
            jaccardParts[0] += matchAdditive(a, b, mappingsA, mappingsB, mappings);
            jaccard = jaccardParts[1] == 0.0 ? 0.0 : jaccardParts[0] / jaccardParts[1];
        }
        return scoreMatchContext(a, b) +
                jaccard * 100.0;
    }

    public static void logMatch(FGPNode a, FGPNode b, FGPNode.NodeMapping mappingsA, FGPNode.NodeMapping mappingsB,
                                    MappingStore mappings) {
        double jaccard;
        if (mappingsA == null && mappingsB == null && mappings == null) {
            jaccard = a.nodeFeatures.jaccardSimilarity(b.nodeFeatures);
        }
        else {
            double jaccardParts[] = a.nodeFeatures.jaccardSimilarityParts(b.nodeFeatures);
            jaccardParts[0] += matchAdditive(a, b, mappingsA, mappingsB, mappings);
            jaccard = jaccardParts[1] == 0.0 ? 0.0 : jaccardParts[0] / jaccardParts[1];
        }
        System.err.println("jaccard=" + jaccard*100.0 + ", context=" + scoreMatchContext(a, b));
    }

    private static double matchAdditive(FGPNode a, FGPNode b, FGPNode.NodeMapping mappingsA, FGPNode.NodeMapping mappingsB,
                                 MappingStore mappings) {
        Set<ITree> dstDescs = new HashSet<>(b.node.getDescendants());
        int additive = 0;

        for (ITree t : a.node.getDescendants()) {
            ITree m = mappings.getDst(t);
            if (m != null && dstDescs.contains(m)) {
                FGPNode x = mappingsA.get(t);
                FGPNode y = mappingsB.get(m);
                if (x.getContentFingerprintIndex() != y.getContentFingerprintIndex()) {
                    additive++;
                }
            }
        }

        return additive;
    }
}
