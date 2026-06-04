package org.lifuscator.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.utils.AsmUtils;
import org.objectweb.asm.tree.*;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "IntegerEncryptor")
public class IntegerEncryptorTransformer extends Transformer {

    @Override
    public void transform(Context context) {
        AtomicInteger count = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            for (MethodNode method : clazz.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    Integer number = AsmUtils.number(instruction);
                    if (number == null) {
                        continue;
                    }

                    int key = random.nextInt();

                    InsnList encrypted = new InsnList();
                    encrypted.add(AsmUtils.numberInsn(number ^ key));
                    encrypted.add(AsmUtils.numberInsn(key));
                    encrypted.add(new InsnNode(IXOR));

                    method.instructions.insert(instruction, encrypted);
                    method.instructions.remove(instruction);

                    count.getAndIncrement();
                }
            }
        }

        log.info("Encrypted {} integers", count.get());
    }
}
