package org.lifuscator.cli;

import org.lifuscator.core.context.Context;

public class Main {

    public static final String ASCII_ART = """
             __    __  ____ __ __  __    ___  ___  ______   ___   ____\s
             ||    || ||    || || (( \\  //   // \\\\ | || |  // \\\\  || \\\\
             ||    || ||==  || ||  \\\\  ((    ||=||   ||   ((   )) ||_//
             ||__| || ||    \\\\_// \\_))  \\\\__ || ||   ||    \\\\_//  || \\\\
                                                                       \
            """;

    public static void main(String[] args) {
        System.out.println(ASCII_ART);
        Context context = new Context(args[0], args[1]);
        context.run();
    }
}
