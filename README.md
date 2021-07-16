# StackAware
ItemStack context for Item methods without it via cursed asm hacks

## What
Some methods in the `Item` class don't pass the ItemStack as context, for example:

`Item#getMaxDamage`, it's prototype is `getMaxDamage()I`, as u can see, the ItemStack is not passed here, so if you wanted to make an item that had nbt-dependent durability, it's not possible
