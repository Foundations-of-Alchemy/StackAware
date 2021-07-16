package io.github.foa.stackaware.impl.asm;

import static io.github.foa.stackaware.impl.StackConstants.GET_ITEM;
import static io.github.foa.stackaware.impl.StackConstants.GET_ITEM_DESC;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

import io.github.foa.stackaware.impl.StackConstants;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class StackTransformer extends ClassVisitor {
	public static final Type ITEM_STACK = Type.getObjectType(StackConstants.ITEM_STACK);
	String owner;
	public boolean transformed;

	public StackTransformer(int api) {
		super(api);
	}

	public StackTransformer(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.owner = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
		return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
			IntList validMethods;

			@Override
			public void visitMethodInsn(int opcodeAndSource, String owner1, String name1, String descriptor1, boolean isInterface) {
				if(owner1.equals(StackConstants.ITEM)) {
					StackConstants.Handler handler = StackConstants.METHODS.get(name1 + descriptor1);
					if(handler != null) {
						if(this.validMethods == null) {
							this.validMethods = new IntArrayList();
						}
						this.validMethods.add(this.instructions.size());
					}
				}
				super.visitMethodInsn(opcodeAndSource, owner1, name1, descriptor1, isInterface);
			}

			@Override
			public void visitEnd() {
				super.visitEnd();
				MethodNode toAccept = this;
				if(this.validMethods != null) {
					transformed = true;
					toAccept = StackTransformer.this.extracted(this, this.validMethods);
				}
				toAccept.accept(visitor);
			}
		};
	}

	private MethodNode extracted(MethodNode original, IntList validMethods) {
		SourceInterpreter interpreter = new SourceInterpreter();
		Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
		try {
			Frame<SourceValue>[] frames = analyzer.analyze(this.owner, original);
			IntListIterator iterator = validMethods.iterator();

			// Item#??? -> getItem
			Int2ObjectMap<IntList> needs = new Int2ObjectOpenHashMap<>();

			// index -> IntList
			// getItem -> [getItem]
			Int2ObjectMap<Set<IntList>> requires = new Int2ObjectOpenHashMap<>();

			Type[] oArg = Type.getArgumentTypes(original.desc);
			int totalArgs = 0;
			for(Type type : oArg) {
				totalArgs += type.getSize();
			}

			while(iterator.hasNext()) {
				int index = iterator.nextInt();
				MethodInsnNode insn = (MethodInsnNode) original.instructions.get(index);
				int args = Type.getArgumentTypes(insn.desc).length; // no real length needed cus weird asm quirk don't ask me

				IntList nodes = new IntArrayList();
				Frame<SourceValue> frame = frames[index];
				this.findAllSources(totalArgs, nodes, original.instructions, frames, frame, frame.getStack(args));
				needs.put(index, nodes);

				nodes.forEach((IntConsumer) i -> requires.computeIfAbsent(i, l -> new HashSet<>()).add(nodes));
			}

			// getItem invocations -> local var ids
			Object2IntMap<IntList> localIndexes = new Object2IntOpenHashMap<>();
			{ // dry run
				var copy = new MethodTransformer(original, requires, localIndexes, needs, true);
				original.accept(copy.sorter);
			}

			Object2IntMap<IntList> newLocalIndexes = new Object2IntOpenHashMap<>();
			var copy = new MethodTransformer(original, requires, newLocalIndexes, needs, false);
			for(IntList list : localIndexes.keySet()) {
				int local = copy.sorter.newLocal(ITEM_STACK);
				newLocalIndexes.put(list, local);
				copy.sorter.visitInsn(Opcodes.ACONST_NULL);
				copy.sorter.visitVarInsn(Opcodes.ASTORE, local);
				copy.addedInstructions += 2;
			}
			original.accept(copy.sorter);
			return copy;
		} catch(AnalyzerException e) {
			throw new RuntimeException(e);
		}
	}

	public void findAllSources(
			int totalArgs,
			IntList nodes,
			InsnList list,
			Frame<SourceValue>[] frames,
			Frame<SourceValue> frame,
			SourceValue value) {
		for(AbstractInsnNode insn : value.insns) {
			if(insn instanceof VarInsnNode v) {
				int var = v.var;
				SourceValue local = frame.getLocal(var); // LOAD
				if(!(var < totalArgs && this.isArg(totalArgs, list, list.indexOf(v), var))) {
					this.findAllSourcesLocal(totalArgs, nodes, list, frames, local);
				}
			} else if(insn instanceof MethodInsnNode n) {
				if(StackConstants.ITEM_STACK.equals(n.owner) && GET_ITEM.equals(n.name) && GET_ITEM_DESC.equals(n.desc)) {
					nodes.add(list.indexOf(n));
				}
			}
		}
	}

	public void findAllSourcesLocal(int totalArgs, IntList nodes, InsnList list, Frame<SourceValue>[] frames, SourceValue value) {
		for(AbstractInsnNode insn : value.insns) {
			if(insn instanceof VarInsnNode v) { // STORE
				Frame<SourceValue> source = frames[list.indexOf(v)];
				this.findAllSources(
				                    totalArgs,
				                    nodes,
				                    list,
				                    frames,
				                    source,
				                    source.getStack(0)); // find where the value on the top of the stack came from
			} else {
				throw new UnsupportedOperationException("tf is " + insn.getClass());
			}
		}
	}

	public boolean isArg(int methodArgs, InsnList list, int instructionIndex, int localIndex) {
		int locals = methodArgs;
		for(int i = 0; i < instructionIndex; i++) {
			if(locals < localIndex) {
				return false;
			}

			AbstractInsnNode node = list.get(i);
			if(node instanceof FrameNode f) {
				switch(f.getType()) {
					case Opcodes.F_NEW:
					case Opcodes.F_FULL:
						locals = f.local.size();
						break;
					case Opcodes.F_APPEND:
						locals += f.local.size();
						break;
					case Opcodes.F_CHOP:
						locals -= f.local.size();
						break;
					default:
						break;
				}
			}
		}
		return locals < localIndex;
	}

	private static class MethodTransformer extends MethodNode {
		final PublicLocalVariablesSorter sorter;
		private final Int2ObjectMap<Set<IntList>> requires;
		private final Object2IntMap<IntList> localIndexes;
		private final Int2ObjectMap<IntList> needs;
		private final boolean dryRun;
		int superLocal;
		int addedInstructions;

		public MethodTransformer(MethodNode original,
				Int2ObjectMap<Set<IntList>> requires,
				Object2IntMap<IntList> localIndexes,
				Int2ObjectMap<IntList> needs, boolean run) {
			super(Opcodes.ASM9, original.access, original.name, original.desc, original.signature, original.exceptions.toArray(String[]::new));
			this.requires = requires;
			this.localIndexes = localIndexes;
			this.needs = needs;
			this.dryRun = run;
			sorter = new PublicLocalVariablesSorter(this.access, this.desc, this);
			superLocal = -1;
			addedInstructions = 0;
		}

		@Override
		public void visitMethodInsn(int opcodeAndSource, String owner, String name, String descriptor, boolean isInterface) {
			int index = this.instructions.size() - this.addedInstructions;
			Set<IntList> insns = requires.get(index);
			if(insns != null) { // insert local variable mode
				if(insns.size() > 1) { // multiple Item calls need this getItem context
					if(this.superLocal == -1) {
						this.superLocal = this.sorter.newLocal(ITEM_STACK);
					}

					this.sorter.visitInsn(Opcodes.DUP);
					this.sorter.visitVarInsn(Opcodes.ASTORE, this.superLocal);
					this.addedInstructions += 2;

					for(IntList insn : insns) {
						int id = localIndexes.computeIntIfAbsent(insn, list -> this.sorter.newLocal(ITEM_STACK));
						this.addedInstructions += 2;
						this.sorter.visitVarInsn(Opcodes.ALOAD, this.superLocal);
						this.sorter.visitVarInsn(Opcodes.ASTORE, id);
					}
				} else { // only one Item call needs this getItem context
					this.addedInstructions += 2;
					this.sorter.visitInsn(Opcodes.DUP);
					int id = localIndexes.computeIntIfAbsent(insns.iterator().next(), list -> this.sorter.newLocal(ITEM_STACK));
					this.sorter.visitVarInsn(Opcodes.ASTORE, id);
				}
			}
			IntList list = needs.get(index);
			if(list != null) {
				int id = localIndexes.computeIntIfAbsent(list, $ -> this.sorter.newLocal(ITEM_STACK));
				this.addedInstructions++;
				this.sorter.visitVarInsn(Opcodes.ALOAD, id);
				StackConstants.Handler handler = StackConstants.METHODS.get(name + descriptor);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, handler.owner(), handler.name(), handler.handlerMethodDesc(), false);
			} else {
				super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
			}
		}
	}
}
