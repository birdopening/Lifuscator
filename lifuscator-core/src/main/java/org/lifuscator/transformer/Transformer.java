package org.lifuscator.transformer;

import org.lifuscator.context.Context;
import org.objectweb.asm.Opcodes;

import java.security.SecureRandom;

public abstract class Transformer implements Opcodes {
    public final SecureRandom random = new SecureRandom();

    public abstract void transform(Context context);
}
