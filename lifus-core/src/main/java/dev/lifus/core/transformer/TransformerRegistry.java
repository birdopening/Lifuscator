package dev.lifus.core.transformer;

import dev.lifus.core.registry.Registry;
import dev.lifus.core.transformer.impl.IntegerEncryptorTransformer;
import dev.lifus.core.transformer.impl.InvokeDynamicTransformer;
import dev.lifus.core.transformer.impl.SourceFileRemoverTransformer;
import dev.lifus.core.transformer.impl.StringEncryptorTransformer;

public class TransformerRegistry extends Registry<Transformer> {

    @Override
    protected void register() {
        add(new SourceFileRemoverTransformer());
        add(new StringEncryptorTransformer());
        add(new IntegerEncryptorTransformer());
        add(new InvokeDynamicTransformer());
    }
}
