package dev.lifus.core.transformer.impl;

import dev.lifus.core.context.Context;
import dev.lifus.core.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public class SourceFileRemoverTransformer extends Transformer {

    @Override
    public String id() {
        return "source-file-remover";
    }

    @Override
    public String name() {
        return "Source File Remover";
    }

    @Override
    public String description() {
        return "Removes sourcefile attributes from every class";
    }

    @Override
    public void transform(Context context) {
        for (ClassNode clazz : context.getJar().classes().values()) {
            clazz.sourceFile = null;
            clazz.sourceDebug = null;
        }
    }
}
