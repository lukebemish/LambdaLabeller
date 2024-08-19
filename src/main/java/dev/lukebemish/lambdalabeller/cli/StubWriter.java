package dev.lukebemish.lambdalabeller.cli;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class StubWriter {
    private final Set<String> names;

    public StubWriter(Set<String> names) {
        this.names = names;
    }

    public void create(ClassVisitor visitor) {
        visitor.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                Shared.LABEL_CLASS,
                null,
                Type.getInternalName(Object.class),
                null
        );
        List<String> namesSorted = new ArrayList<>(names);
        namesSorted.sort(Comparator.naturalOrder());
        for (var name : namesSorted) {
            var method = visitor.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    name,
                    "()V",
                    null,
                    new String[0]
            );
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitEnd();
        }
        visitor.visitEnd();
    }
}
