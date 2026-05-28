package org.lifuscator.transformer.impl;

import org.lifuscator.context.Context;
import org.lifuscator.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public class SourceFileRemoverTransformer extends Transformer {

    @Override
    public void transform(Context context) {
        for (ClassNode clazz : context.getClasses().values()) {
            clazz.sourceFile = null;
            clazz.sourceDebug = null;
        }
    }
}
