package io.github.foa.stackaware.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.google.gson.internal.UnsafeAllocator;
import io.github.foa.stackaware.impl.vist.ClassPoolSearcher;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.FabricMixinTransformerProxy;

public class StackAwareImpl implements IMixinConfigPlugin {
	static {
		try {
			List.of(StackConstants.class, StackMixinTransformerProxy.class, ClassPoolSearcher.class);

			Object knot = StackAwareImpl.class.getClassLoader();
			Method getDelegate = knot.getClass().getDeclaredMethod("getDelegate");
			getDelegate.setAccessible(true);
			Object delegate = getDelegate.invoke(knot);
			Field mixinTransformer = delegate.getClass().getDeclaredField("mixinTransformer");
			mixinTransformer.setAccessible(true);
			UnsafeAllocator allocator = UnsafeAllocator.create();
			Class<?> cls = StackMixinTransformerProxy.class;
			Object proxy = allocator.newInstance(cls);
			((StackMixinTransformerProxy) proxy).delegate = (FabricMixinTransformerProxy) mixinTransformer.get(delegate);
			mixinTransformer.set(delegate, proxy);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	// @formatter:off
	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() {return null;}
	@Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return false;}
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() {return null;}
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
