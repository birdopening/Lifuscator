package org.lifuscator.core.transformer;

import org.lifuscator.core.context.Context;
import org.lifuscator.core.registry.IFeature;
import org.objectweb.asm.Opcodes;

import java.security.SecureRandom;

public abstract class Transformer implements Opcodes, IFeature {
    public final SecureRandom random = new SecureRandom();

    public abstract void transform(Context context);
}
