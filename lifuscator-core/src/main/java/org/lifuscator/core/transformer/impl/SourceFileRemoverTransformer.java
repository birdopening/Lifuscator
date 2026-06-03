package org.lifuscator.core.transformer.impl;

import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public class SourceFileRemoverTransformer extends Transformer {

    @Override
    public void transform(Context context) {
        for (ClassNode clazz : context.getJar().classes().values()) {
            clazz.sourceFile = null;
            clazz.sourceDebug = null;
        }
    }
}
