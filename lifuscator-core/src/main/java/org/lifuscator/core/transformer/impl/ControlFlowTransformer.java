package org.lifuscator.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import org.lifuscator.core.analysis.BasicBlock;
import org.lifuscator.core.analysis.ControlFlowAnalyzer;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "ControlFlow")
public class ControlFlowTransformer extends Transformer {

    private final ControlFlowAnalyzer cfa = new ControlFlowAnalyzer();

    @Override
    public void transform(Context context) {
        AtomicInteger methodCount = new AtomicInteger(0);
        AtomicInteger blockCount = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            for (MethodNode method : clazz.methods) {
                if (method.instructions.size() == 0) {
                    continue;
                }

                List<BasicBlock> blocks = cfa.analyze(method);
                methodCount.incrementAndGet();
                blockCount.addAndGet(blocks.size());
            }
        }

        log.info("Analyzed {} methods into {} basic blocks", methodCount.get(), blockCount.get());
    }

    private Map<BasicBlock, Integer> assignKeys(List<BasicBlock> blocks) {
        Map<BasicBlock, Integer> keys = new HashMap<>();
        Set<Integer> takenKeys = new HashSet<>();

        for (BasicBlock block : blocks) {
            int key;
            do {
                key = random.nextInt();
            } while (!takenKeys.add(key));

            keys.put(block, key);
        }

        return keys;
    }
}
