package io.github.foa.stackaware.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.github.foa.stackaware.impl.asm.StackTransformer;
import io.github.foa.stackaware.impl.vist.ClassPoolSearcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.FabricMixinTransformerProxy;

public class StackMixinTransformerProxy extends FabricMixinTransformerProxy {

	FabricMixinTransformerProxy delegate;

	@Override
	public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
		basicClass = this.delegate.transformClassBytes(name, transformedName, basicClass);
		if(basicClass != null && ClassPoolSearcher.read(name, basicClass)) {
			ClassReader reader = new ClassReader(basicClass);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			StackTransformer transformer = new StackTransformer(Opcodes.ASM9, writer);
			reader.accept(transformer, ClassReader.SKIP_DEBUG | ClassReader.EXPAND_FRAMES);

			if(transformer.transformed) {
				System.out.println(name);
				byte[] array = writer.toByteArray();
				// == debug ==
				File file = new File(name.replace('.', '/') + ".class");
				file.getParentFile().mkdirs();
				try(FileOutputStream fos = new FileOutputStream(file)) {
					fos.write(array);
				} catch(IOException e) {
					e.printStackTrace();
				}
				// == debug ==
				return array;
			}

		}
		return basicClass;
	}
}