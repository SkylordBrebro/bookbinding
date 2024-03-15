package surrender.bookbind.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class BookBindMixin {

	@Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
	public void onBookClick(ItemStack slotStack, ItemStack cursorStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference, CallbackInfoReturnable<Boolean> cir) {
		if (slotStack.getItem() == Items.ENCHANTED_BOOK && cursorStack.getItem() == Items.AIR && clickType == ClickType.RIGHT) {
			// Get the number of empty slots directly without iterating over the inventory
			NbtCompound ebookNBT = slotStack.getOrCreateNbt();
			NbtList ebookEnchants = ebookNBT.getList("StoredEnchantments", NbtElement.COMPOUND_TYPE);

			if (emptySlotCount(player) >= ebookEnchants.size()) {
				slotStack.decrement(1);

				// Use ItemStack.copy to avoid modifying the original ItemStack
				for (int i = 0; i < ebookEnchants.size(); i++) {
					ItemStack paper = new ItemStack(Items.PAPER, ebookEnchants.getCompound(i).getInt("lvl"));
					NbtCompound paperNBT = paper.getOrCreateNbt();

					// Store the enchantment directly into the paper's NBT without intermediate steps
					NbtList storedEnchantsNBT = new NbtList();
					NbtCompound storedEnchantNBT = new NbtCompound();
					storedEnchantNBT.putString("id", ebookEnchants.getCompound(i).getString("id"));
					storedEnchantNBT.putInt("lvl", 1);
					storedEnchantsNBT.add(storedEnchantNBT);
					paperNBT.put("StoredEnchantments", storedEnchantsNBT);

					// Set the name of the paper using Text.of directly
					String enchantName = (ebookEnchants.getCompound(i).getString("id").replace("minecraft:", ""));
					enchantName = enchantName.substring(0, 1).toUpperCase() + enchantName.substring(1);
					paper.setCustomName(Text.of(enchantName));

					// Enchant the paper with infinity directly without intermediate steps
					paper.addEnchantment(Registries.ENCHANTMENT.get(new Identifier("minecraft:infinity")), 1);
					paperNBT.putInt("HideFlags", 1);

					player.getInventory().offerOrDrop(paper);
				}
				cir.setReturnValue(true);
			} else {
				cir.setReturnValue(false);
			}
			return;
		} else if (slotStack.getItem() == Items.ENCHANTED_BOOK && cursorStack.getItem() == Items.PAPER) {
			if (cursorStack.getOrCreateNbt().contains("StoredEnchantments")) {
				combineEnchantments(cursorStack, slotStack);
				cir.setReturnValue(true);
				return;
			}
		} else if (emptySlotCount(player) > 1 && slotStack.getItem() == Items.PAPER && cursorStack.getItem() == Items.LEATHER) {
			turnPaperIntoEnchantedBook(player, slotStack, cursorStack);
			cir.setReturnValue(true);
			return;
		}
		cir.setReturnValue(false);
	}

	@Unique
	private void turnPaperIntoEnchantedBook(PlayerEntity player, ItemStack slotStack, ItemStack cursorStack) {
		if (slotStack.getOrCreateNbt().contains("StoredEnchantments")) {
			ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK, 1);
			enchantedBook.getOrCreateNbt().put("StoredEnchantments", slotStack.getOrCreateNbt().getList("StoredEnchantments", NbtElement.COMPOUND_TYPE));
			cursorStack.decrement(1);
			slotStack.decrement(1);
			player.getInventory().offerOrDrop(enchantedBook);
		}
	}
	@Unique
	private int emptySlotCount(PlayerEntity player) {
		int eSlotCount = 0;
		for (int i = 0; i < player.getInventory().size()-4; i++) {
			if (player.getInventory().getStack(i).getItem() == Items.AIR) {
				eSlotCount += 1;
			}
		}
        return eSlotCount;
    }

	@Unique
	private void combineEnchantments(ItemStack cursorStack, ItemStack slotStack) {
		ItemStack cursorCopy = cursorStack.copy();
		NbtCompound cursorNBT = cursorCopy.getOrCreateNbt();
		if (cursorNBT.contains("StoredEnchantments", NbtElement.LIST_TYPE)) {
			NbtList cursorEnchants = cursorNBT.getList("StoredEnchantments", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < cursorEnchants.size(); i++) {
				NbtCompound enchantmentTag = cursorEnchants.getCompound(i);
				String enchantmentId = enchantmentTag.getString("id");
				int level = enchantmentTag.getInt("lvl");
				Enchantment enchantment = Registries.ENCHANTMENT.get(new Identifier(enchantmentId));
				if (enchantment != null && level > 0) {
					NbtList targetEnchants = slotStack.getOrCreateNbt().getList("StoredEnchantments", NbtElement.COMPOUND_TYPE);
					for (int j = 0; j < targetEnchants.size(); j++) {
						NbtCompound targetEnchantmentTag = targetEnchants.getCompound(j);
						String targetEnchantmentId = targetEnchantmentTag.getString("id");
						int targetLevel = targetEnchantmentTag.getInt("lvl");
						if (targetEnchantmentId.equals(enchantmentId)) {
							if (level + 1 <= enchantment.getMaxLevel()) {
								targetEnchantmentTag.putInt("lvl", targetLevel + 1);
								cursorStack.decrement(1);
							}
							return;
						}
					}
					NbtCompound newEnchantmentTag = new NbtCompound();
					newEnchantmentTag.putString("id", enchantmentId);
					newEnchantmentTag.putInt("lvl", level);
					targetEnchants.add(newEnchantmentTag);
					cursorStack.decrement(1);
				}
			}
		}
	}
}

