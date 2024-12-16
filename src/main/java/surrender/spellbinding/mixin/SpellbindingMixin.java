package surrender.spellbinding.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class SpellbindingMixin {

	@Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
	public void onBookClick(ItemStack slotStack, ItemStack cursorStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference, CallbackInfoReturnable<Boolean> cir) {
		if (slotStack.getItem() == Items.ENCHANTED_BOOK && cursorStack.getItem() == Items.AIR && clickType == ClickType.RIGHT) {
			// Get the number of empty slots directly without iterating over the inventory
			ItemEnchantmentsComponent ebookEnchants = EnchantmentHelper.getEnchantments(slotStack);
			for (Object2IntMap.Entry<RegistryEntry<Enchantment>> enchantment : ebookEnchants.getEnchantmentEntries()) {
				for (int i = 0; i < enchantment.getIntValue(); i++) {
					ItemStack paper = new ItemStack(Items.PAPER);
					paper.addEnchantment(enchantment.getKey(), 1);
					paper.set(DataComponentTypes.CUSTOM_NAME, Text.translatable(String.valueOf(enchantment.getKey().value()).replace("Enchantment ", "")));
					player.getInventory().offerOrDrop(paper);
				}
			}
			slotStack.decrement(1);
			cir.setReturnValue(true);
		} else if (slotStack.getItem() == Items.LEATHER && cursorStack.getItem() == Items.PAPER && cursorStack.hasEnchantments() && clickType == ClickType.LEFT) {
			// Get the enchantment from the paper
			ItemEnchantmentsComponent paperEnchants = EnchantmentHelper.getEnchantments(cursorStack);
			if (paperEnchants.getSize() != 1) {
				return;
			}

			// Create a new enchanted book
			ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
			enchantedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, paperEnchants);

			// Remove one leather and one enchanted paper
			slotStack.decrement(1);
			cursorStack.decrement(1);

			// Give the player the enchanted book
			player.getInventory().offerOrDrop(enchantedBook);

			cir.setReturnValue(true);
		} else if (slotStack.getItem() == Items.ENCHANTED_BOOK && cursorStack.getItem() == Items.PAPER && cursorStack.hasEnchantments() && clickType == ClickType.LEFT) {
			ItemEnchantmentsComponent bookEnchants = EnchantmentHelper.getEnchantments(slotStack);
			ItemEnchantmentsComponent paperEnchants = EnchantmentHelper.getEnchantments(cursorStack);

			// Ensure the paper has exactly one enchantment
			if (paperEnchants.getSize() != 1) {
				return;
			}

			RegistryEntry<Enchantment> paperEnchantmentEntry = paperEnchants.getEnchantmentEntries().iterator().next().getKey();
			Enchantment paperEnchantment = paperEnchantmentEntry.value();

			// Use EnchantmentHelper.apply to modify the enchantments
			EnchantmentHelper.apply(slotStack, builder -> {
				int currentLevel = bookEnchants.getLevel(paperEnchantmentEntry);
				int maxLevel = paperEnchantment.getMaxLevel();

				if (currentLevel < maxLevel) {
					// Enchantment can be increased
					builder.set(paperEnchantmentEntry, currentLevel + 1);
					cursorStack.decrement(1);
					cir.setReturnValue(true);
				}
			});

		}
	}
}