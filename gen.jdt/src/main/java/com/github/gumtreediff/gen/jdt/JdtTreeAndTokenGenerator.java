package com.github.gumtreediff.gen.jdt;

import com.github.gumtreediff.gen.Register;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Geoff on 27/05/2016.
 */
@Register(id = "java-jdt-gt", accept = "\\.java$" )
public class JdtTreeAndTokenGenerator extends TreeGenerator {
    private static boolean OPTIONS_INITIALISED = false;

    private static boolean SIMPLIFY = false;

    private static void initOptions() {
        if (!OPTIONS_INITIALISED) {
            String simplifyProp = System.getProperty("gumtree.gen.jdt.simplify");
            if (simplifyProp != null) {
                System.err.println("Setting gumtree.gen.jdt.simplify is " + simplifyProp);
                SIMPLIFY = Boolean.parseBoolean(simplifyProp);
            }
            OPTIONS_INITIALISED = true;
        }
    }



    private static char[] readerToCharArray(Reader r) throws IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader br = new BufferedReader(r);

        char[] buf = new char[10];
        int numRead = 0;
        while ((numRead = br.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
        }
        br.close();

        return  fileData.toString().toCharArray();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public TreeContext generate(Reader r) throws IOException {
        initOptions();

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        Map pOptions = JavaCore.getOptions();
        pOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
        parser.setCompilerOptions(pOptions);
        char srcText[] = readerToCharArray(r);

        // Tokenise
        IScanner scanner = ToolFactory.createScanner(true, true, true, true);
        scanner.setSource(srcText);
        ArrayList<JdtToken> tokens = new ArrayList<>();
        try {
            int tokenType = scanner.getNextToken();
            while (scanner.getCurrentTokenStartPosition() < srcText.length) {
                int start = scanner.getCurrentTokenStartPosition();
                int end = scanner.getCurrentTokenEndPosition();
                char tkSrc[] = scanner.getRawTokenSource();
                JdtToken token = new JdtToken(tokenType, tkSrc, start, end + 1);
                tokens.add(token);
                tokenType = scanner.getNextToken();
            }
        } catch (InvalidInputException e) {
        }

        parser.setSource(srcText);
        JdtTreeAndTokenVisitor v = new JdtTreeAndTokenVisitor(tokens.toArray(new JdtToken[tokens.size()]), SIMPLIFY);
        parser.createAST(null).accept(v);
        return v.getTreeContext();
    }

}
