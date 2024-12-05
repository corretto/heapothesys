package com.amazon.corretto.benchmark.hyperalloc;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;

public class RunReport implements Closeable {

    private FileWriter fw;

    public RunReport(String path) throws IOException {
        fw = new FileWriter(path,true);
    }

    public void write(String message) throws IOException {
        fw.write(message);
        fw.write(",");
    }

    public void write(int value) throws IOException {
        this.write(Integer.toString(value));
    }

    public void write(long value) throws IOException {
        this.write(Long.toString(value));
    }

    public void eol() throws IOException {
        fw.write("\n");
    }

    public void write(boolean value) throws IOException {
        this.write(Boolean.toString(value));
    }

    @Override
    public void close() throws IOException {
        fw.close();
    }
}
