package com.github.gumtreediff.test_samples;

import com.github.gumtreediff.gen.jdt.JdtTreeAndTokenGenerator;
import com.github.gumtreediff.tree.TreeContext;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Geoff on 31/05/2016.
 */
public class TestTreeAndTokenVisitor {
    public static void main(String argv[]) {
        String p1 = "../treediffbench/src/org/soft_dev/treediffbench/matchers/fingerprint/FingerprintMatcher.java";
//        String p2 = "src/org/soft_dev/treediffbench/actions/ActionGenerator.java";

        String s1 = null;
//        String s2 = null;
        try {
            byte[] encoded1 = Files.readAllBytes(Paths.get(p1));
//            byte[] encoded2 = Files.readAllBytes(Paths.get(p2));
            s1 = new String(encoded1, "UTF-8");
//            s2 = new String(encoded2, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Reader rA = new StringReader(s1);
//        Reader rB = new StringReader(contentB);
        JdtTreeAndTokenGenerator gen = new JdtTreeAndTokenGenerator();
        TreeContext tA = null;
//        TreeContext tB = null;
        try {
            tA = gen.generate(rA);
//            tB = gen.generate(rB);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
//        tA.validate();
//        tB.validate();
    }
}
