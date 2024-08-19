package dev.lukebemish.lambdalabeller.cli;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.LambdaMetafactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class LambdaRenamer extends ClassVisitor {
    private final Map<String, String> renames = new HashMap<>();

    LambdaRenamer(Map<String, String> renames, Set<String> unknownNames, ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
        this.renames.putAll(renames);
        for (var name : unknownNames) {
            if (this.renames.containsValue(name)) {
                renames.put(name, name + "$1");
            }
        }
    }

    private static final String LAMBDA_METAFACTORY = Type.getInternalName(LambdaMetafactory.class);

    private String currentClass;

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.currentClass = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, delegate) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (!owner.equals(Shared.LABEL_CLASS)) {
                    super.visitMethodInsn(opcode, owner, renames.getOrDefault(name, name), descriptor, isInterface);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                if (!bootstrapMethodHandle.getOwner().equals(LAMBDA_METAFACTORY)) {
                    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    return;
                }
                Object[] newBootstrapMethodArguments = new Object[bootstrapMethodArguments.length];
                for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                    var argument = bootstrapMethodArguments[i];
                    if (argument instanceof Handle handle) {
                        if (handle.getOwner().equals(currentClass)) {
                            newBootstrapMethodArguments[i] = new Handle(
                                    handle.getTag(),
                                    handle.getOwner(),
                                    renames.getOrDefault(handle.getName(), handle.getName()),
                                    handle.getDesc(),
                                    handle.isInterface()
                            );
                            continue;
                        }
                    }
                    newBootstrapMethodArguments[i] = argument;
                }
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, newBootstrapMethodArguments);
            }
        };
    }
}
