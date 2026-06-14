package org.lifuscator.core.transformer;

import org.lifuscator.core.registry.Registry;
import org.lifuscator.core.transformer.impl.IntegerEncryptorTransformer;
import org.lifuscator.core.transformer.impl.InvokeDynamicTransformer;
import org.lifuscator.core.transformer.impl.SourceFileRemoverTransformer;
import org.lifuscator.core.transformer.impl.StringEncryptorTransformer;

public class TransformerRegistry extends Registry<Transformer> {

    @Override
    protected void register() {
        add(new SourceFileRemoverTransformer());
        add(new StringEncryptorTransformer());
        add(new IntegerEncryptorTransformer());
        add(new InvokeDynamicTransformer());
    }
}
