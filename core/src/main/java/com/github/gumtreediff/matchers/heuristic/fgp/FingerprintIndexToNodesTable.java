package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.ArrayList;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FingerprintIndexToNodesTable {
    private ArrayList<ArrayList<FGPNode>> nodesByIndex = new ArrayList<ArrayList<FGPNode>>();

    void addNode(int fingerprintIndex, FGPNode node) {
        for (int i = nodesByIndex.size(); i <= fingerprintIndex; i++) {
            nodesByIndex.add(new ArrayList<>());
        }

        nodesByIndex.get(fingerprintIndex).add(node);
    }

    void union(FingerprintIndexToNodesTable b) {
        FingerprintIndexToNodesTable result = new FingerprintIndexToNodesTable();

        int size = Math.max(nodesByIndex.size(), b.nodesByIndex.size());
        for (int i = 0; i < size; i++) {
            result.nodesByIndex.add(new ArrayList<>());
        }

        int commonSize = Math.min(nodesByIndex.size(), b.nodesByIndex.size());
        for (int i = 0; i < commonSize; i++) {
            result.nodesByIndex.get(i).addAll(nodesByIndex.get(i));
            result.nodesByIndex.get(i).addAll(b.nodesByIndex.get(i));
        }

        for (int i = commonSize; i < nodesByIndex.size(); i++) {
            result.nodesByIndex.get(i).addAll(nodesByIndex.get(i));
        }

        for (int i = commonSize; i < b.nodesByIndex.size(); i++) {
            result.nodesByIndex.get(i).addAll(b.nodesByIndex.get(i));
        }
    }
}
