package com.github.gumtreediff.matchers.optimal;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.Pair;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Created by Geoff on 03/03/2016.
 */
@Register(id = "memo")
public class MemoisedMatcher extends Matcher {
    private static class TreeHashEntry {
        private String sha256Hex;
        private int index;
        private int featureVector[] = null;
        private int weight = 0;

        private TreeHashEntry(String sha256Hex, int index) {
            this.sha256Hex = sha256Hex;
            this.index = index;
        }

        private void featuresInit(int N) {
            featureVector = new int[N];
            featureVector[index] = 1;
            weight = 1;
        }

        private void featuresAccum(TreeHashEntry src) {
            for (int i = 0; i < src.featureVector.length; i++) {
                featureVector[i] += src.featureVector[i];
            }
            weight += src.weight;
        }

        private double featureDistanceTo(TreeHashEntry x) {
            double dist = 0.0;
            for (int i = 0; i < featureVector.length; i++) {
                dist += Math.abs(featureVector[i] - x.featureVector[i]);
            }
            return dist;
        }
    }

    private static class TreeHashes {
        private HashMap<String, TreeHashEntry> uniqueHashes = new HashMap<>();
        private IdentityHashMap<ITree, TreeHashEntry> hashes = new IdentityHashMap<>();
        private double distanceMatrix[];
        private int numHashes = 0;
        private int distCount = 0;

        private TreeHashes() {
        }


        private TreeHashEntry uniqueHash(String sha256Hex) {
            TreeHashEntry entry = uniqueHashes.get(sha256Hex);
            if (entry == null) {
                entry = new TreeHashEntry(sha256Hex, uniqueHashes.size());
                uniqueHashes.put(sha256Hex, entry);
            }
            return entry;
        }

        private TreeHashEntry hashFor(ITree node) {
            TreeHashEntry entry = hashes.get(node);
            if (entry == null) {
                StringBuilder src = new StringBuilder();
                src.append(String.valueOf(node.getType()));
                src.append("(");
                boolean first = true;
                for (ITree child: node.getChildren()) {
                    if (!first) {
                        src.append(",");
                    }
                    src.append(hashFor(child).sha256Hex);
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
                String sha256Hex = DatatypeConverter.printHexBinary(digest);
                entry = uniqueHash(sha256Hex);
                hashes.put(node, entry);
            }
            return entry;
        }

        private TreeHashEntry buildFeatureVectorFor(ITree node) {
            TreeHashEntry entry = hashFor(node);
            if (entry.featureVector == null) {
                entry.featuresInit(uniqueHashes.size());

                for (ITree child: node.getChildren()) {
                    TreeHashEntry x = buildFeatureVectorFor(child);
                    entry.featuresAccum(x);
                }
            }
            return entry;
        }

        private void addTrees(ITree trees[]) {
            for (ITree tree: trees) {
                hashFor(tree);
            }
            for (ITree tree: trees) {
                buildFeatureVectorFor(tree);
            }
            numHashes = uniqueHashes.size();
            distanceMatrix = new double[numHashes*numHashes];
            for (int i = 0; i < distanceMatrix.length; i++) {
                distanceMatrix[i] = -1.0;
            }
        }

        public int numUniqueHashes() {
            return uniqueHashes.size();
        }

        private double featureDistance(TreeHashEntry a, TreeHashEntry b) {
            int i = a.index * numHashes + b.index;
            int i2 = b.index * numHashes + a.index;
            if (distanceMatrix[i] == -1.0) {
                distanceMatrix[i2] = distanceMatrix[i] = a.featureDistanceTo(b);
                distCount += 1;
            }
            return distanceMatrix[i];
        }

        private double featureDistance(ITree nodeA, ITree nodeB) {
            return featureDistance(hashFor(nodeA), hashFor(nodeB));
        }

        private int totalDistCount() {
            return (numHashes * numHashes + numHashes) / 2;
        }
    }


    private class Edit {
        private double editDistance;
        private ArrayList<Integer> matchesA, matchesB;
        private ArrayList<Edit> matchEdits;

        private Edit(double editDistance, ArrayList<Integer> matchesA, ArrayList<Integer> matchesB,
                     ArrayList<Edit> matchEdits) {
            this.editDistance = editDistance;
            this.matchesA = matchesA;
            this.matchesB = matchesB;
            this.matchEdits = matchEdits;
        }
    }

    private Edit[][] editDistanceMemo;
    private TreeHashes hashes = new TreeHashes();

    public MemoisedMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    @Override
    public void match() {
        hashes.addTrees(new ITree[] {src, dst});

        // Create the edit distance memo table
        int n = hashes.numUniqueHashes();
        editDistanceMemo = new Edit[n][];
        for (int i = 0; i < n; i++) {
            editDistanceMemo[i] = new Edit[n];
        }

        // Compute edit distance for roots
        System.out.println("src.getSize()=" + src.getSize());
        System.out.println("dst.getSize()=" + dst.getSize());
        Edit e = editDist(src, dst);

        int mappingCount = generateMappings(src, dst, e);

        System.out.println("Computed " + hashes.distCount + "/" + hashes.totalDistCount() + " feature distances, with feature vectors of length " + hashes.numHashes);
        System.out.println("Mapped " + mappingCount + " nodes with distance " + e.editDistance);

    }

    private int generateMappings(ITree a, ITree b, Edit edit) {
        int mappingCount = 0;
        if (a.getType() == b.getType()) {
            addMapping(a, b);
            mappingCount += 1;
        }
        for (int i = 0; i < edit.matchesA.size(); i++) {
            mappingCount += generateMappings(a.getChild(edit.matchesA.get(i)),
                                             b.getChild(edit.matchesB.get(i)),
                                             edit.matchEdits.get(i));
        }
        return mappingCount;
    }

    private static int OP_DEL = 0x1;
    private static int OP_INS = 0x2;
    private static int OP_UPD = 0x4;

    private double weightFor(ITree a) {
        return hashes.hashFor(a).weight;
    }

    private Edit editDist(ITree a, ITree b) {
        TreeHashEntry ha = hashes.hashFor(a);
        TreeHashEntry hb = hashes.hashFor(b);
        int aKey = ha.index;
        int bKey = hb.index;
        Edit edit = editDistanceMemo[aKey][bKey];
        if (edit == null) {
            List<ITree> cA = a.getChildren();
            List<ITree> cB = b.getChildren();
            int nA = cA.size();
            int nB = cB.size();
            ArrayList<Integer> matchesA = new ArrayList<>(), matchesB = new ArrayList<>();
            ArrayList<Edit> matchEdits = new ArrayList<>();
            double dist = a.getType() == b.getType() ? 0.0 : 1.0;
            if (nA == 0 && nB == 0) {
                // Nothing to do
            }
            else if (nA != 0 && nB == 0) {
                // All nodes in A deleted
                for (int i = 0; i < nA; i++) {
                    dist += cA.get(i).getSize();
                }
            }
            else if (nA == 0 && nB != 0) {
                // All nodes in B inserted
                for (int j = 0; j < nB; j++) {
                    dist += cB.get(j).getSize();
                }
            }
            else {
                int P = nA + 1, Q = nB + 1;
                double distMatrix[] = new double[P*Q];
                int opMatrix[] = new int[P*Q];
                for (int i = 1; i <= nA; i++) {
                    distMatrix[i*Q+0] = distMatrix[(i-1)*Q+0] + cA.get(i-1).getSize();
                    opMatrix[i*Q+0] = OP_DEL;
                }
                for (int j = 1; j <= nB; j++) {
                    distMatrix[0*Q+j] = distMatrix[0*Q+(j-1)] + cB.get(j-1).getSize();
                    opMatrix[0*Q+j] = OP_INS;
                }
                for (int i = 1; i <= nA; i++) {
                    for (int j = 1; j <= nB; j++) {
                        double delCost = distMatrix[(i-1)*Q+j] + (double)cA.get(i-1).getSize();
                        double insCost = distMatrix[i*Q+(j-1)] + (double)cB.get(j-1).getSize();
                        Edit chEdt = editDist(cA.get(i-1), cB.get(j-1));
                        double updCost = distMatrix[(i-1)*Q+(j-1)] + chEdt.editDistance;

                        double minCost = Math.min(Math.min(delCost, insCost), updCost);
                        distMatrix[i*Q+j] = minCost;
                        opMatrix[i*Q+j] = (delCost == minCost ? OP_DEL : 0) |
                                (insCost == minCost ? OP_INS : 0) |
                                (updCost == minCost ? OP_UPD : 0);
                    }
                }
                dist += distMatrix[nA*Q+nB];
                int i = nA, j = nB;
                while (!(i == 0 && j==0)) {
                    int op = opMatrix[i*Q+j];
                    if ((op & OP_UPD) != 0) {
                        Edit chEdt = editDist(cA.get(i-1), cB.get(j-1));
                        matchesA.add(i-1);
                        matchesB.add(j-1);
                        matchEdits.add(chEdt);
                        i -= 1;
                        j -= 1;
                    }
                    else if ((op & OP_INS) != 0) {
                        j -= 1;
                    }
                    else if ((op & OP_DEL) != 0) {
                        i -= 1;
                    }
                }
                double featDist = hashes.featureDistance(ha, hb) * 2.0;
                double basicDist = dist;
                dist = basicDist * 0.5 + featDist * 0.5;
            }
            edit = new Edit(dist, matchesA, matchesB, matchEdits);
            editDistanceMemo[aKey][bKey] = edit;
        }
        return edit;
    }


}
