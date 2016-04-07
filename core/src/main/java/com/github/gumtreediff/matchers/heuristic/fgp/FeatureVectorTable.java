package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.ArrayList;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVectorTable {
    FingerprintTable fingerprints = new FingerprintTable();
    ArrayList<FeatureVector> featsByIndex = new ArrayList<>();


    public FingerprintIndexToNodesTable addTree(FGPNode tree) {
        FingerprintIndexToNodesTable indexToNodes = new FingerprintIndexToNodesTable();
        tree.updateFingerprintIndex(fingerprints, indexToNodes);

        for (int i = featsByIndex.size(); i < fingerprints.size(); i++) {
            featsByIndex.add(null);
        }

        buildTreeFeaturesBottomUp(tree);
        buildNodeFeaturesTopDown(tree, new FeatureVector(fingerprints.size()), new FeatureVector(fingerprints.size()));

        return indexToNodes;
    }

    private void buildTreeFeaturesBottomUp(FGPNode root) {
        int n = fingerprints.size();
        root.leftSiblingsFeats = new FeatureVector(n);
        root.rightSiblingsFeats = new FeatureVector(n);
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
            cumulativeChildFeats[0] = new FeatureVector(fingerprints.size());

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
        int fg = node.getFingerprintIndex();
        FeatureVector feats = featsByIndex.get(fg);
        if (feats == null) {
            feats = new FeatureVector(fingerprints.size());
            feats.set(fg, 1);

            for (FGPNode child: node.children) {
                feats.accum(child.nodeFeatures);
            }
        }

        node.nodeFeatures = feats;
    }

    private void buildNodeFeaturesTopDown(FGPNode node, FeatureVector fgLeft, FeatureVector fgRight) {
        node.leftTreeFeats = fgLeft;
        node.rightTreeFeats = fgRight;

        for (FGPNode child: node.children) {
            buildNodeFeaturesTopDown(child, fgLeft.add(child.leftSiblingsFeats), fgRight.add(child.rightSiblingsFeats));
        }
    }


    public static double scoreMatchContext(FGPNode a, FGPNode b) {
        return a.leftTreeFeats.jaccardSimilarity(b.leftTreeFeats) +
                a.rightTreeFeats.jaccardSimilarity(b.rightTreeFeats) +
                a.leftSiblingsFeats.jaccardSimilarity(b.leftSiblingsFeats) * 10.0 +
                a.rightSiblingsFeats.jaccardSimilarity(b.rightSiblingsFeats) * 10.0;
    }

    public static double scoreMatch(FGPNode a, FGPNode b) {
        return scoreMatchContext(a, b) +
                a.nodeFeatures.jaccardSimilarity(b.nodeFeatures) * 100.0;
    }
}
