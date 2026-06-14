package org.lifuscator.cli;

import org.lifuscator.core.Lifuscator;
import org.lifuscator.core.config.Config;
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

        if (args.length < 2) {
            System.err.println("Usage: lifuscator <input.jar> <output.jar>");
            System.exit(1);
        }

        Config config = Config.of(args[0], args[1]);
        Context context = new Context(config);

        boolean success = new Lifuscator().run(context);
        if (!success) {
            System.exit(1);
        }
    }
}
