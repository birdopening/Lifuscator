package org.lifuscator.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import org.lifuscator.core.analysis.BasicBlock;
import org.lifuscator.core.analysis.ControlFlowAnalyzer;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.utils.AsmUtils;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "ControlFlow")
public class ControlFlowTransformer extends Transformer {

    @Override
    public void transform(Context context) {
        AtomicInteger methodCount = new AtomicInteger(0);
        AtomicInteger blockCount = new AtomicInteger(0);
        AtomicInteger flattened = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            for (MethodNode method : clazz.methods) {
                if (method.instructions.size() == 0) {
                    continue;
                }

                List<BasicBlock> blocks = ControlFlowAnalyzer.analyze(method);
                methodCount.incrementAndGet();
                blockCount.addAndGet(blocks.size());

                if (flatten(clazz, method)) {
                    flattened.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
            }
        }

        log.info("Analyzed {} methods into {} basic blocks", methodCount.get(), blockCount.get());
        log.info("Flattened {} methods (skipped: {})", flattened.get(), skipped.get());
    }

    private boolean flatten(ClassNode clazz, MethodNode method) {

        //TODO
        if (!method.tryCatchBlocks.isEmpty()) return false;
        if (method.name.equals("<init>")) return false;

        Frame<BasicValue>[] frames = computeFrames(clazz.name, method);
        if (frames == null) return false;

        Map<AbstractInsnNode, Integer> indexOf = new HashMap<>();
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            indexOf.put(insns[i], i);
        }

        List<BasicBlock> blocks = ControlFlowAnalyzer.analyze(method);
        if (blocks.size() < 2) return false;

        Set<BasicBlock> deadCode = new HashSet<>();
        Set<BasicBlock> targets = new HashSet<>(); // empty stack
        for (BasicBlock block : blocks) {
            Frame<BasicValue> frame = frames[indexOf.get(block.first())];
            if (frame == null) {
                deadCode.add(block);
            } else if (frame.getStackSize() == 0) {
                targets.add(block);
            }
        }

        if (!targets.contains(blocks.getFirst())) return false; // ALWAYS EMPTY


        Map<List<String>, List<BasicBlock>> groups = new LinkedHashMap<>();
        for (BasicBlock target : targets) {
            List<String> key = localsSig(frames[indexOf.get(target.first())]);
            groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(target);
        }

        Map<BasicBlock, LabelNode> dispatcherOf = new HashMap<>();
        Map<List<String>, LabelNode> groupLabels = new LinkedHashMap<>();
        for (List<String> key : groups.keySet()) {
            LabelNode dispatcher = new LabelNode();
            groupLabels.put(key, dispatcher);
            for (BasicBlock target : groups.get(key)) {
                // 1 dispatcher per group, not per block
                dispatcherOf.put(target, dispatcher);
            }
        }

        int stateSlot = allocState(method);
        Map<BasicBlock, Integer> keys = assignKeys(blocks);
        Map<BasicBlock, LabelNode> labels = createLabels(blocks);
        LabelNode dispatcherLabel = new LabelNode();

        Map<BasicBlock, InsnList> tails = new HashMap<>();
        for (BasicBlock block : blocks) {
            InsnList tail = blockTail(block, stateSlot, dispatcherLabel, keys);
            if (tail == null) return false;
            tails.put(block, tail);
        }

        method.instructions.clear();

        method.instructions.add(AsmUtils.numberInsn(keys.get(blocks.getFirst())));
        method.instructions.add(new VarInsnNode(ISTORE, stateSlot));

        method.instructions.add(lookupswitchDispatcher(stateSlot, dispatcherLabel, blocks, keys, labels));

        for (BasicBlock block : blocks) {
            method.instructions.add(labels.get(block));
            for (AbstractInsnNode insn : block.getInstructions()) {
                method.instructions.add(insn);
            }
            method.instructions.add(tails.get(block));
        }

        return true;
    }

    private List<String> localsSig(Frame<BasicValue> frame) {
        List<String> key = new ArrayList<>(frame.getLocals());
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue local = frame.getLocal(i);
            key.add(local == null || local.getType() == null ? "_" : local.getType().getDescriptor());
        }
        return key;
    }

    private Frame<BasicValue>[] computeFrames(String owner, MethodNode method) {
        try {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            return analyzer.analyze(owner, method);
        } catch (AnalyzerException e) {
            return null;
        }
    }

    private int allocState(MethodNode method) {
        int slot = method.maxLocals;
        method.maxLocals += 1;
        return slot;
    }

    private InsnList lookupswitchDispatcher(int stateSlot, LabelNode dispatcherLabel, List<BasicBlock> blocks, Map<BasicBlock, Integer> keys, Map<BasicBlock, LabelNode> labels) {
        InsnList insnList = new InsnList();

        insnList.add(dispatcherLabel);
        insnList.add(new VarInsnNode(ILOAD, stateSlot));

        List<BasicBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(keys::get));

        int[] switchKeys = new int[sorted.size()];
        LabelNode[] switchLabels = new LabelNode[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            BasicBlock block = sorted.get(i);
            switchKeys[i] = keys.get(block); // number
            switchLabels[i] = labels.get(block); // Label
        }

        // can never happen
        LabelNode def = labels.get(blocks.getFirst());

        insnList.add(new LookupSwitchInsnNode(def, switchKeys, switchLabels));
        return insnList;
    }

    private InsnList blockTail(BasicBlock block, int stateSlot, LabelNode dispatcherLabel, Map<BasicBlock, Integer> keys) {
        AbstractInsnNode last = block.last();
        int opcode = last.getOpcode();

        if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
            return new InsnList(); // control already gone
        }

        if (opcode == GOTO) {
            block.getInstructions().remove(last); //useless
            BasicBlock target = block.getSuccessors().getFirst();
            return gotoDispatcher(stateSlot, keys.get(target), dispatcherLabel);
        }

        boolean b = last instanceof JumpInsnNode || last instanceof TableSwitchInsnNode || last instanceof LookupSwitchInsnNode;

        // fallthrough?
        if (!b) {
            if (block.getSuccessors().isEmpty()) { //TODO fix
                return new InsnList();
            }
            BasicBlock next = block.getSuccessors().getFirst();
            return gotoDispatcher(stateSlot, keys.get(next), dispatcherLabel);
        }

        if (last instanceof TableSwitchInsnNode || last instanceof LookupSwitchInsnNode) {
            List<BasicBlock> succ = block.getSuccessors();

            Map<BasicBlock, LabelNode> stubs = new HashMap<>();
            InsnList tail = new InsnList();

            if (last instanceof TableSwitchInsnNode tableswitch) {
                tableswitch.dflt = switchStub(succ.getFirst(), stubs, tail, stateSlot, keys, dispatcherLabel);
                for (int i = 0; i < tableswitch.labels.size(); i++) {
                    tableswitch.labels.set(i, switchStub(succ.get(i + 1), stubs, tail, stateSlot, keys, dispatcherLabel));
                }
            } else {
                LookupSwitchInsnNode lookupswitch = (LookupSwitchInsnNode) last;
                lookupswitch.dflt = switchStub(succ.getFirst(), stubs, tail, stateSlot, keys, dispatcherLabel);
                for (int i = 0; i < lookupswitch.labels.size(); i++) {
                    lookupswitch.labels.set(i, switchStub(succ.get(i + 1), stubs, tail, stateSlot, keys, dispatcherLabel));
                }
            }

            return tail;
        }

        // conditional jumps
        if (last instanceof JumpInsnNode jump) {
            BasicBlock taken = block.getSuccessors().get(0); // jump (true)
            BasicBlock notTaken = block.getSuccessors().get(1); // fallthrough (false)


            LabelNode takenLabel = new LabelNode();
            jump.label = takenLabel;

            InsnList tail = new InsnList();
            tail.add(gotoDispatcher(stateSlot, keys.get(notTaken), dispatcherLabel));
            tail.add(takenLabel);
            tail.add(gotoDispatcher(stateSlot, keys.get(taken), dispatcherLabel));
            return tail;
        }

        return null;
    }


    private LabelNode switchStub(BasicBlock target, Map<BasicBlock, LabelNode> stubs, InsnList tail, int stateSlot, Map<BasicBlock, Integer> keys, LabelNode dispatcherLabel) {
        LabelNode stub = stubs.get(target);
        if (stub == null) {
            stub = new LabelNode();
            stubs.put(target, stub);
            tail.add(stub);
            tail.add(gotoDispatcher(stateSlot, keys.get(target), dispatcherLabel));
        }
        return stub;
    }

    private InsnList gotoDispatcher(int stateSlot, int nextKey, LabelNode dispatcherLabel) {
        InsnList insnList = new InsnList();
        insnList.add(AsmUtils.numberInsn(nextKey));
        insnList.add(new VarInsnNode(ISTORE, stateSlot));
        insnList.add(new JumpInsnNode(GOTO, dispatcherLabel));
        return insnList;
    }

    private Map<BasicBlock, LabelNode> createLabels(List<BasicBlock> blocks) {
        Map<BasicBlock, LabelNode> labels = new HashMap<>();
        for (BasicBlock block : blocks) {
            labels.put(block, new LabelNode()); // block code start
        }
        return labels;
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
