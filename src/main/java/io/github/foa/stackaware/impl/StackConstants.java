package io.github.foa.stackaware.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Type;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class StackConstants {
	protected static final MappingResolver RESOLVER = FabricLoader.getInstance().getMappingResolver();
	// we don't need to worry about subclasses of items being referenced directly, if a mod uses stack aware but doesn't call stack aware methods, that's their fault.
	public static final String ITEM_INTERMEDIARY = "net.minecraft.class_1792";
	public static final String ITEM = RESOLVER.mapClassName("intermediary", ITEM_INTERMEDIARY).replace('.', '/');
	public static final ByteBuffer ITEM_IN_BUF = ByteBuffer.wrap(ITEM.getBytes(StandardCharsets.UTF_8));
	public static final String ITEM_STACK_INTERMEDIARY = "net.minecraft.class_1799";
	public static final String ITEM_STACK = RESOLVER.mapClassName("intermediary", ITEM_STACK_INTERMEDIARY).replace('.', '/');
	public static final String GET_ITEM_DESC_INTERMEDIARY = "()L" + ITEM_INTERMEDIARY.replace('.', '/') + ";";
	public static final String GET_ITEM_DESC = "()L" + ITEM + ";";
	public static final String GET_ITEM = RESOLVER.mapMethodName("intermediary", ITEM_STACK_INTERMEDIARY, "method_7909", GET_ITEM_DESC_INTERMEDIARY);

	public static final Set<ByteBuffer> KEY_WORDS = new HashSet<>();
	public static final Map<String, Handler> METHODS = new HashMap<>();

	public record Handler(String owner, String name, String handlerMethodDesc) {}

	static void register(String itemMethodName, String mappedMethodDesc, String handlerMethodName) {
		String mappedName = RESOLVER.mapMethodName("intermediary", ITEM_INTERMEDIARY, itemMethodName, mappedMethodDesc);
		KEY_WORDS.add(ByteBuffer.wrap(mappedName.getBytes(StandardCharsets.UTF_8)));
		Type type = Type.getMethodType(mappedMethodDesc);
		Type[] newArgs = ArrayUtils.add(ArrayUtils.add(type.getArgumentTypes(), 0, Type.getObjectType(ITEM)), Type.getObjectType(ITEM_STACK));
		Type handlerType = Type.getMethodType(type.getReturnType(), newArgs);
		METHODS.put(mappedName + mappedMethodDesc, new Handler("io/github/foa/stackaware/StackHandlers", handlerMethodName, handlerType.toString()));
	}

	static {
		register("method_7841", "()I", "getMaxDamage");
		register("method_7882", "()I", "getMaxCount");
		register("method_7887", "()Z", "isNBTSynced");
		register("method_7858", "()L" + ITEM + ";", "getRecipeRemainder");
		//KEY_WORDS.add(ByteBuffer.wrap(ITEM_INTERNAL.replace('.', '/').getBytes(StandardCharsets.UTF_8)));
	}

}
