package com.apisniffer.logging;

import com.apisniffer.model.InterceptedTraffic;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Thread-safe writer for appending intercepted traffic entries to a JSON Lines (.jsonl) file.
 */
public class JsonLineWriter implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final File outputFile;
    private final BufferedWriter writer;
    private final boolean noBody;

    public JsonLineWriter(File file, boolean noBody) throws Exception {
        this.outputFile = file;
        this.noBody = noBody;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Open in append mode with UTF-8
        this.writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8));
    }

    public synchronized void writeEntry(InterceptedTraffic traffic) {
        try {
            InterceptedTraffic entryToWrite = traffic;
            if (noBody) {
                // Clone without headers and bodies for compact no-body mode
                entryToWrite = new InterceptedTraffic();
                entryToWrite.setId(traffic.getId());
                entryToWrite.setTimestamp(traffic.getTimestamp());
                entryToWrite.setDurationMs(traffic.getDurationMs());
                entryToWrite.setMethod(traffic.getMethod());
                entryToWrite.setUrl(traffic.getUrl());
                entryToWrite.setBaseUrl(traffic.getBaseUrl());
                entryToWrite.setPath(traffic.getPath());
                entryToWrite.setScheme(traffic.getScheme());
                entryToWrite.setHost(traffic.getHost());
                entryToWrite.setPort(traffic.getPort());
                entryToWrite.setStatusCode(traffic.getStatusCode());
                entryToWrite.setStatusMessage(traffic.getStatusMessage());
            }

            String line = MAPPER.writeValueAsString(entryToWrite);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to write entry to " + outputFile.getName() + ": " + e.getMessage());
        }
    }

    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public synchronized void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }
}
