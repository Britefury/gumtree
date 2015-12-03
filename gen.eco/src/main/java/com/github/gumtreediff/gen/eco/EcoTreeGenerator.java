/*
 * This file is a temporary shim layer to allow Eco and Gumtree to communicate.
*/

package com.github.gumtreediff.gen.eco;

import com.github.gumtreediff.gen.Register;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.google.gson.*;


import java.io.IOException;
import java.io.Reader;

@Register(id = "eco", accept = "\\.ecotree$")
public class EcoTreeGenerator extends TreeGenerator {
    /*
    Handles trees of the form:

    {
        "__type__": "EcoTree",
        "tree": <tree>
    }

    where <tree> is an ordered tree of nodes, of the form:

    {
        "__type__": "TreeNode",
        "label": string,
        "type": integer,
        "type_label": string,
        "pos": integer,
        "length": integer,
        "children": [array of child nodes]
    }
     */

    private static String DOC_TYPE = "EcoTree";

    private String getObjectType(JsonElement objectElem) {
        if (objectElem != null && objectElem.isJsonObject()) {
            JsonElement typeElem = ((JsonObject)objectElem).get("__type__");
            if (typeElem != null) {
                return typeElem.getAsString();
            }
        }
        return null;
    }

    private ITree buildNode(TreeContext ctx, JsonObject nodeElem) {
        JsonElement labelElem = nodeElem.get("label");
        JsonElement typeElem = nodeElem.get("type");
        JsonElement typeLabelElem = nodeElem.get("type_label");
        JsonElement posElem = nodeElem.get("pos");
        JsonElement lengthElem = nodeElem.get("length");
        JsonElement childrenElem = nodeElem.get("children");
        String label = labelElem != null ? labelElem.getAsString() : "";
        int type = typeElem != null ? typeElem.getAsInt() : -1;
        String typeLabel = typeLabelElem != null ? typeLabelElem.getAsString() : ITree.NO_LABEL;
        int pos = posElem != null ? posElem.getAsInt() : 0;
        int length = lengthElem != null ? lengthElem.getAsInt() : 0;
        ITree t = ctx.createTree(type, label, typeLabel);
        t.setPos(pos);
        t.setLength(length);
        if (childrenElem != null) {
            JsonArray childrenArr = childrenElem.getAsJsonArray();
            if (childrenArr != null) {
                int n = childrenArr.size();
                for (int i = 0; i < n; i++) {
                    JsonElement childElem = childrenArr.get(i);
                    if (childElem != null && childElem.isJsonObject()) {
                        ITree childNode = buildNode(ctx, childElem.getAsJsonObject());
                        t.addChild(childNode);
                    }
                }
            }
        }
        return t;
    }

    private ITree buildTree(TreeContext ctx, JsonObject docElem) {
        JsonElement treeElem = docElem.get("tree");
        if (treeElem != null && treeElem.isJsonObject()) {
            return buildNode(ctx, treeElem.getAsJsonObject());
        }
        return null;
    }


    public TreeContext generate(Reader r) throws IOException {
        JsonParser parser = new JsonParser();
        JsonElement doc = parser.parse(r);
        if (getObjectType(doc).equals(DOC_TYPE)) {
            TreeContext ctx = new TreeContext();
            ITree tree = buildTree(ctx, (JsonObject)doc);
            ctx.setRoot(tree);
            return ctx;
        }
        return null;
    }
}
