package com.apisniffer;

import com.apisniffer.cli.ApiSnifferCli;
import picocli.CommandLine;

/**
 * Main application entry point for ApiSniffer.
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiSnifferCli()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
