package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.tree.ITree;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FGPNode {
    protected ITree node;

    protected FGPNode children[];

    protected int depth = 0, subtreeSize = 0;
    protected boolean matched = false;

    private String shapeSha;
    private int fingerprintIndex = -1;
    protected FeatureVector nodeFeatures = null;
    protected FeatureVector leftSiblingsFeats = null, rightSiblingsFeats = null;
    protected FeatureVector leftTreeFeats = null, rightTreeFeats = null;


    public FGPNode(ITree node) {
        this.node = node;

        depth = 0;
        subtreeSize = 0;

        this.children = new FGPNode[node.getChildren().size()];
        for (int i = 0; i < children.length; i++) {
            FGPNode childNode = new FGPNode(node.getChild(i));
            this.children[i] = childNode;

            depth = Math.max(depth, childNode.depth);
            subtreeSize += childNode.subtreeSize;
        }

        depth += 1;
        subtreeSize += 1;
    }


    public String getShapeSha() {
        if (shapeSha == null) {
            StringBuilder src = new StringBuilder();
            src.append(String.valueOf(node.getType()));
            src.append("(");
            boolean first = true;
            for (FGPNode child: children) {
                if (!first) {
                    src.append(",");
                }
                src.append(child.getShapeSha());
                first = false;
            }
            src.append(")");
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            byte[] digest = md.digest(src.toString().getBytes());
            shapeSha = DatatypeConverter.printHexBinary(digest);
        }
        return shapeSha;
    }

    public void updateFingerprintIndex(FingerprintTable fingerprints, FingerprintIndexToNodesTable indexToNodes) {
        if (fingerprintIndex == -1) {
            for (FGPNode node: children) {
                node.updateFingerprintIndex(fingerprints, indexToNodes);
            }
            fingerprintIndex = fingerprints.getIndexForSha(getShapeSha());
            indexToNodes.addNode(fingerprintIndex, this);
        }
    }

    public int getFingerprintIndex() {
        return fingerprintIndex;
    }
}
