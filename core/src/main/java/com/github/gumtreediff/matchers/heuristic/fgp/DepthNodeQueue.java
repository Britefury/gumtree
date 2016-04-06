package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Geoff on 06/04/2016.
 */
public class DepthNodeQueue {
    protected int maxDepth;
    protected ArrayList<FGPNode> nodesByDepth[];

    public DepthNodeQueue(FGPNode root) {
        maxDepth = root.depth;
        nodesByDepth = new ArrayList[maxDepth+1];
        for (int i = 0; i < nodesByDepth.length; i++) {
            nodesByDepth[i] = new ArrayList<>();
        }
        nodesByDepth[maxDepth].add(root);
    }

    protected void pushNodes(List<FGPNode> nodes) {
        for (FGPNode node: nodes) {
            int nodeDepth = node.depth;
            nodesByDepth[nodeDepth].add(node);
            maxDepth = Math.max(maxDepth, nodeDepth);
        }
    }

    protected void pushNodes(FGPNode nodes[]) {
        for (FGPNode node: nodes) {
            int nodeDepth = node.depth;
            nodesByDepth[nodeDepth].add(node);
            maxDepth = Math.max(maxDepth, nodeDepth);
        }
    }

    protected ArrayList<FGPNode> popNodesAtMaxDepth() {
        if (maxDepth == 0) {
            return null;
        }
        else {
            ArrayList<FGPNode> nodes = nodesByDepth[maxDepth];
            nodesByDepth[maxDepth] = new ArrayList<>();
            while (maxDepth > 0 && nodesByDepth[maxDepth].size() == 0) {
                maxDepth--;
            }
            return nodes;
        }
    }
}
