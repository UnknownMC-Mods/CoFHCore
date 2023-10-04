package cofh.core.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

public abstract class BlockCore extends Block {

	protected String modName;
	protected String name;

	public BlockCore(Material material, String modName) {

		super(material);
		this.modName = modName;
	}

	public BlockCore(Material material, MapColor blockMapColor, String modName) {

		super(material, blockMapColor);
		this.modName = modName;
	}

	@Override
	public Block setTranslationKey(String name) {

		this.name = name;
		name = modName + "." + name;
		return super.setTranslationKey(name);
	}

	public String getTranslationKey(ItemStack stack) {

		return getTranslationKey();
	}

	public EnumRarity getRarity(ItemStack stack) {

		return EnumRarity.COMMON;
	}

}
