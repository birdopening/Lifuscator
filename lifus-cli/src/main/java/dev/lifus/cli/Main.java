package dev.lifus.cli;

import dev.lifus.core.Lifus;
import dev.lifus.core.config.Config;
import dev.lifus.core.context.Context;

public class Main {

    public static final String ASCII_ART = "                                                  \n" +
            " ▄▄▄▄         ██        ▄▄▄▄                ▄     \n" +
            " ▀▀██         ▀▀       ██▀▀▀              ▄▄█▄▄   \n" +
            "   ██       ████     ███████   ██    ██  ██▀█▀▀   \n" +
            "   ██         ██       ██      ██    ██  ▀███▄▄   \n" +
            "   ██         ██       ██      ██    ██     █▀██  \n" +
            "   ██▄▄▄   ▄▄▄██▄▄▄    ██      ██▄▄▄███  █▄▄█▄██  \n" +
            "    ▀▀▀▀   ▀▀▀▀▀▀▀▀    ▀▀       ▀▀▀▀ ▀▀   ▀▀█▀▀   \n" +
            "                                            ▀     \n" +
            "                                                  ";

    static void main(String[] args) {
        System.out.println(ASCII_ART);

        if (args.length < 2) {
            System.err.println("Usage: lifus <input.jar> <output.jar>");
            System.exit(1);
        }

        Config config = Config.of(args[0], args[1]);
        Context context = new Context(config);

        boolean success = new Lifus().run(context);
        if (!success) {
            System.exit(1);
        }
    }
}
