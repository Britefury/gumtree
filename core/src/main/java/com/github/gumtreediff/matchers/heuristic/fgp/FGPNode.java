package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.tree.ITree;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FGPNode {
    public static class NodeMapping {
        private ArrayList<FGPNode> nodeIDToFGPNode = new ArrayList<>();

        void registerNode(ITree srcNode, FGPNode dstNode) {
            int nodeId = srcNode.getId();
            for (int i = nodeIDToFGPNode.size(); i <= nodeId; i++) {
                nodeIDToFGPNode.add(null);
            }
            nodeIDToFGPNode.set(nodeId, dstNode);
        }

        public FGPNode get(ITree srcNode) {
            return nodeIDToFGPNode.get(srcNode.getId());
        }

        public FGPNode getByID(int nodeId) {
            return nodeIDToFGPNode.get(nodeId);
        }
    }

    protected ITree node;

    protected FGPNode parent;
    protected FGPNode children[];

    protected int depth = 0, subtreeSize = 0;
    protected boolean matched = false;

    private String shapeSha, contentSha;
    private int shapeFGIndex = -1, contentFGIndex = -1;
    protected FeatureVector nodeFeatures = null;
    protected FeatureVector leftSiblingsFeats = null, rightSiblingsFeats = null, parentContainmentFeatures = null;
    protected double leftTree, rightTree;


    public FGPNode(ITree node, NodeMapping mapping) {
        this.node = node;
        mapping.registerNode(node, this);

        depth = 0;
        subtreeSize = 0;

        this.children = new FGPNode[node.getChildren().size()];
        for (int i = 0; i < children.length; i++) {
            FGPNode childNode = new FGPNode(node.getChild(i), mapping);
            this.children[i] = childNode;
            childNode.parent = this;

            depth = Math.max(depth, childNode.depth);
            subtreeSize += childNode.subtreeSize;
        }

        depth += 1;
        subtreeSize += 1;
    }


    public String getSha(boolean content) {
        String sha = content ? contentSha : shapeSha;
        if (sha == null) {
            StringBuilder src = new StringBuilder();
            src.append(String.valueOf(node.getType()));
            if (content) {
                src.append("[");
                src.append(node.getLabel());
                src.append("](");
            }
            else {
                src.append("(");
            }
            boolean first = true;
            for (FGPNode child: children) {
                if (!first) {
                    src.append(",");
                }
                src.append(child.getSha(content));
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
            sha = DatatypeConverter.printHexBinary(digest);
            if (content) {
                contentSha = sha;
            }
            else {
                shapeSha = sha;
            }
        }
        return sha;
    }

    public void updateFingerprintIndex(FingerprintTable shapeFingerprints, FingerprintTable contentFingerprints) {
        if (shapeFingerprints != null && shapeFGIndex == -1 ||
            contentFingerprints != null && contentFGIndex == -1) {
            for (FGPNode node: children) {
                node.updateFingerprintIndex(shapeFingerprints, contentFingerprints);
            }
            if (shapeFingerprints != null) {
                shapeFGIndex = shapeFingerprints.getIndexForSha(getSha(false));
            }
            if (contentFingerprints != null) {
                contentFGIndex = contentFingerprints.getIndexForSha(getSha(true));
            }
        }
    }

    public int getShapeFingerprintIndex() {
        return shapeFGIndex;
    }

    public int getContentFingerprintIndex() {
        return contentFGIndex;
    }

    public Iterable<FGPNode> depthFirst() {
        return new Iterable<FGPNode>() {
            @Override
            public Iterator<FGPNode> iterator() {
                Stack<FGPNode> stack = new Stack<>();
                stack.add(FGPNode.this);

                return new Iterator<FGPNode>() {
                    @Override
                    public boolean hasNext() {
                        return !stack.isEmpty();
                    }

                    @Override
                    public FGPNode next() {
                        FGPNode node = stack.pop();
                        for (int i = node.children.length - 1; i >= 0; i--) {
                            stack.add(node.children[i]);
                        }
                        return node;
                    }
                };
            }
        };
    }

    public boolean isInSubtreeRootedAt(FGPNode subtreeRoot) {
        FGPNode node = this;
        while (node != null) {
            if (node == subtreeRoot) {
                return true;
            }
            node = node.parent;
        }
        return false;
    }
}
