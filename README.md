# StackAware
ItemStack context for Item methods without it via cursed asm hacks

## What
Some methods in the `Item` class don't pass the ItemStack as context, for example:

`Item#getMaxDamage`, it's prototype is `getMaxDamage()I`, as u can see, the ItemStack is not passed here, so if you wanted to make an item that had nbt-dependent durability, it's not possible

## Why
I need to be able to hide all the items in the mod, and I can do that in NBT (since I can encrypt that), however certain methods don't pass it, so now this exists
