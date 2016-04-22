package com.github.gumtreediff.tree.hash;

import com.github.gumtreediff.tree.ITree;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Geoff on 18/04/2016.
 */
public class HashDictionaryGenerator implements HashGenerator {
    private HashMap<String, Integer> digestToHashCode = new HashMap<>();
    private ArrayList<String> hashCodeToDigest = new ArrayList<>();


    private String nodeString(ITree t) {
        StringBuilder b = new StringBuilder();
        b.append(String.valueOf(t.getType()));
        b.append("[");
        b.append(t.getLabel());
        b.append("](");
        for (ITree c: t.getChildren()) {
            b.append(nodeDigest(c));
            b.append(",");
        }
        b.append(")");
        return b.toString();
    }

    private static String stringDigest(String x) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        byte[] digest = md.digest(x.getBytes());
        return DatatypeConverter.printHexBinary(digest);
    }

    private int nodeHashCode(ITree t) {
        String digest = stringDigest(nodeString(t));
        Integer hashCode = digestToHashCode.get(digest);
        if (hashCode == null) {
            int p = hashCodeToDigest.size();
            hashCodeToDigest.add(digest);
            digestToHashCode.put(digest, p);
            t.setHash(p);
            return p;
        }
        else {
            t.setHash(hashCode);
            return hashCode;
        }
    }

    private String nodeDigest(ITree t) {
        return hashCodeToDigest.get(nodeHashCode(t));
    }

    @Override
    public void hash(ITree t) {
        nodeHashCode(t);
    }
}
