package com.github.gumtreediff.matchers;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Created by Geoff on 15/06/2016.
 */
public abstract class AbstractMatchStats {
    public abstract void asJson(JsonWriter jsonOut) throws IOException;

    @Override
    public abstract String toString();
}
