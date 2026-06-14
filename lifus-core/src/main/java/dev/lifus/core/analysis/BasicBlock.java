package dev.lifus.core.analysis;

import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BasicBlock {
    private final int index;

    private final List<AbstractInsnNode> instructions = new ArrayList<>();
    private final List<BasicBlock> successors = new ArrayList<>();
    private final List<BasicBlock> predecessors = new ArrayList<>();

    public BasicBlock(int index) {
        this.index = index;
    }

    public AbstractInsnNode first() {
        return instructions.isEmpty() ? null : instructions.getFirst();
    }

    public AbstractInsnNode last() {
        return instructions.isEmpty() ? null : instructions.getLast();
    }
}
