package cofh.core.command;

import cofh.CoFHCore;
import com.google.common.base.Throwables;
import gnu.trove.iterator.hash.TObjectHashIterator;
import gnu.trove.set.hash.THashSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

public class CommandClearBlock implements ISubCommand {

	public static CommandClearBlock INSTANCE = new CommandClearBlock();

	public static int permissionLevel = 3;

	public static void config() {

		String category = "Command." + INSTANCE.getCommandName();
		String comment = "Adjust this value to change the default permission level for the " + INSTANCE.getCommandName() + " command.";
		permissionLevel = CoFHCore.CONFIG_CORE.getConfiguration().getInt("PermissionLevel", category, permissionLevel, -1, 4, comment);
	}

	/* ISubCommand */
	@Override
	public String getCommandName() {

		return "clearblocks";
	}

	@Override
	public int getPermissionLevel() {

		return permissionLevel;
	}

	@Override
	public void handleCommand(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {

		if (args.length < 6) {
			sender.sendMessage(new TextComponentTranslation("chat.cofh.command.syntaxError"));
			throw new WrongUsageException("chat.cofh.command." + getCommandName() + ".syntax");
		}
		World world = sender.getEntityWorld();
		if (world.isRemote) {
			return;
		}
		BlockPos center = null;
		int i = 1;
		int xS, xL;
		if ("@".equals(args[i])) {
			center = sender.getPosition();
			++i;
			xS = CommandBase.parseInt(args[i++]);
		} else {
			try {
				xS = CommandBase.parseInt(args[i++]);
			} catch (Throwable t) {
				center = CommandBase.getPlayer(server, sender, args[i - 1]).getPosition();
				xS = CommandBase.parseInt(args[i++]);
			}
		}
		int yS = CommandBase.parseInt(args[i++]), yL;
		int zS = CommandBase.parseInt(args[i++]), zL;
		int t = i + 1;

		try {
			xL = CommandBase.parseInt(args[i++]);
			yL = CommandBase.parseInt(args[i++]);
			zL = CommandBase.parseInt(args[i++]);
		} catch (Throwable e) {
			if (i > t || center == null) {
				throw Throwables.propagate(e);
			}
			--i;
			xL = xS;
			yL = yS;
			zL = zS;
		}
		if (center != null) {
			xS = center.getX() - xS;
			yS = center.getY() - yS;
			zS = center.getZ() - zS;

			xL = center.getX() + xL;
			yL = center.getY() + yL;
			zL = center.getZ() + zL;
		}
		yS &= ~yS >> 31; // max(yS, 0)
		yL &= ~yL >> 31; // max(yL, 0)

		if (xL < xS) {
			t = xS;
			xS = xL;
			xL = t;
		}
		if (yL < yS) {
			t = yS;
			yS = yL;
			yL = t;
		}
		if (zL < zS) {
			t = zS;
			zS = zL;
			zL = t;
		}
		if (yS > 255) {
			sender.sendMessage(new TextComponentTranslation("chat.cofh.command.syntaxError"));
			sender.sendMessage(new TextComponentTranslation("chat.cofh.command." + getCommandName() + ".syntax"));
			return;
		} else if (yL > 255) {
			yL = 255;
		}
		long blockCounter = ((long) xL - xS) * ((long) yL - yS) * ((long) zL - zS);
		CommandHandler.logAdminCommand(sender, this, "chat.cofh.command.clearblocks.start", blockCounter, xS, yS, zS, xL, yL, zL);

		THashSet<Chunk> set = new THashSet<>();

		blockCounter = 0;
		for (int e = args.length; i < e; ++i) {
			String blockRaw = args[i];
			if (blockRaw.charAt(0) == '*') {
				if (blockRaw.equals("*fluid")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								IBlockState state = chunk.getBlockState(pos);
								if (state.getMaterial().isLiquid()) {
									if (chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*tree")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos cPos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(cPos);
								if (state.getBlock().isWood(world, bPos) || state.getBlock().isLeaves(state, world, bPos)) {
									++blockCounter;
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.startsWith("*repl")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								if (state.getBlock().isReplaceable(world, bPos)) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*stone")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								Block block = state.getBlock();
								if (block.isReplaceableOreGen(state, world, bPos, BlockMatcher.forBlock(Blocks.STONE)) || block.isReplaceableOreGen(state, world, bPos, BlockMatcher.forBlock(Blocks.NETHERRACK)) || block.isReplaceableOreGen(state, world, bPos, BlockMatcher.forBlock(Blocks.END_STONE))) {
									++blockCounter;
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*rock")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								if (state.getMaterial() == Material.ROCK) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*sand")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								if (state.getMaterial() == Material.SAND) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*dirt")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								Material m = state.getMaterial();
								if (m == Material.GRASS || m == Material.GROUND || m == Material.CLAY || m == Material.SNOW || m == Material.CRAFTED_SNOW || m == Material.ICE || m == Material.PACKED_ICE) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.startsWith("*plant")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								Material m = state.getMaterial();
								if (m == Material.PLANTS || m == Material.VINE || m == Material.CACTUS || m == Material.LEAVES) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				} else if (blockRaw.equals("*fire")) {
					for (int x = xS; x <= xL; ++x) {
						for (int z = zS; z <= zL; ++z) {
							Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
							int cX = x & 15, cZ = z & 15;
							for (int y = yS; y <= yL; ++y) {
								BlockPos pos = new BlockPos(cX, y, cZ);
								BlockPos bPos = new BlockPos(x, y, z);
								IBlockState state = chunk.getBlockState(pos);
								Material m = state.getMaterial();
								if (m == Material.FIRE || m == Material.LAVA || state.getBlock().isBurning(world, bPos)) {
									if (chunk.setBlockState(bPos, Blocks.AIR.getDefaultState()) != null) {
										++blockCounter;
										set.add(chunk);
									}
								}
							}
						}
					}
				}
				continue;
			}
			int meta = -1;
			t = blockRaw.indexOf('#');
			if (t > 0) {
				meta = CommandBase.parseInt(blockRaw.substring(t + 1));
				blockRaw = blockRaw.substring(0, t);
			}
			Block block = Block.getBlockFromName(blockRaw);
			if (block == Blocks.AIR) {
				continue;
			}
			for (int x = xS; x <= xL; ++x) {
				for (int z = zS; z <= zL; ++z) {
					Chunk chunk = world.getChunk(new BlockPos(x, 0, z));
					int cX = x & 15, cZ = z & 15;
					for (int y = yS; y <= yL; ++y) {
						IBlockState state = chunk.getBlockState(new BlockPos(cX, y, cZ));
						boolean v = meta == -1 || state.getBlock().getMetaFromState(state) == meta;
						if (v && state.getBlock() == block) {
							if (chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState()) != null) {
								++blockCounter;
								set.add(chunk);
							}
						}
					}
				}
			}
		}
		if (!set.isEmpty()) {
			CommandHandler.logAdminCommand(sender, this, "chat.cofh.command.clearblocks.success", blockCounter, xS, yS, zS, xL, yL, zL);
		} else {
			CommandHandler.logAdminCommand(sender, this, "chat.cofh.command.clearblocks.failure");
		}
		if (world instanceof WorldServer) {
			TObjectHashIterator<Chunk> c = set.iterator();
			for (int k = 0, e = set.size(); k < e; ++k) {
				Chunk chunk = c.next();
				PlayerChunkMap manager = ((WorldServer) world).getPlayerChunkMap();
				if (manager == null) {
					return;
				}
				PlayerChunkMapEntry watcher = manager.getEntry(chunk.x, chunk.z);
				if (watcher != null) {
					watcher.sendPacket(new SPacketChunkData(chunk, -1));
				}
			}
		}
	}

	@Override
	public List<String> addTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args) {

		if (args.length == 2) {
			return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
		}
		return null;
	}

}
