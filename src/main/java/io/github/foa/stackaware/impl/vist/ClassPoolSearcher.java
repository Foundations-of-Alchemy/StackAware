package io.github.foa.stackaware.impl.vist;

import java.nio.ByteBuffer;

import io.github.foa.stackaware.impl.StackConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class ClassPoolSearcher implements Opcodes {
	// todo more robust checking, this'll work more or less ok in intermediary though
	public static boolean read(String name, byte[] code) {
		if(name.contains("StemBlock")) {
			new ClassReader(code);
		}
		ByteBuffer buffer = ByteBuffer.wrap(code);
		if(buffer.getInt() != 0xCAFEBABE) {
			throw new IllegalArgumentException("Invalid Magic!");
		}
		buffer.getShort(); // minor version
		int major;
		if((major = unsign(buffer.getShort())) > V17) {
			throw new UnsupportedClassVersionError(name + " has class major version larger than supported (" + major + ">" + V17 + ")");
		}

		boolean containsMethod = false, containsItem = false;
		int count = unsign(buffer.getShort());
		for(int cp = 0; cp < count-1; cp++) {
			byte tag = buffer.get(); // 301 is the one that's erroring
			//https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-4.html#jvms-4.4.1
			switch(tag) {
				case 1 -> {  // utf8
					int len = unsign(buffer.getShort());
					int current = buffer.position();
					ByteBuffer buf = buffer.slice(current, len);
					buffer.position(current + len);
					if(StackConstants.KEY_WORDS.contains(buf)) {
						containsMethod = true;
					} else if(StackConstants.ITEM_IN_BUF.equals(buf)) {
						containsItem = true;
					}

					if(containsItem && containsMethod) {
						return true;
					}
				}
				case 7, 8, 16, 19, 20 -> buffer.position(buffer.position() + 2);
				case 3, 4, 9, 10, 11, 12, 17, 18 -> buffer.position(buffer.position() + 4);
				case 5, 6 -> {
					buffer.position(buffer.position() + 8);
					cp++;
				}
				case 15 -> buffer.position(buffer.position() + 3);
			}
		}
		return false;
	}

	static int unsign(short s) {
		return s & 0xFFFF;
	}
}
