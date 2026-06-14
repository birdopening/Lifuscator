package dev.lifus.core.transformer;

import dev.lifus.core.context.Context;
import dev.lifus.core.registry.IFeature;
import org.objectweb.asm.Opcodes;

import java.security.SecureRandom;

public abstract class Transformer implements Opcodes, IFeature {
    public final SecureRandom random = new SecureRandom();

    public abstract void transform(Context context);
}
