package org.lifuscator.cli;

import org.lifuscator.core.context.Context;

public class Main {

    public static void main(String[] args) {
        Context context = new Context(args[0], args[1]);
        context.run();
    }
}
