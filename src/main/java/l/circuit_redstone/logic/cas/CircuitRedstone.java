package l.circuit_redstone.logic.cas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l.circuit_redstone.logic.cas.command.CircuitDebugCommand;
import l.circuit_redstone.logic.cas.command.CircuitReadCommand;
import l.circuit_redstone.logic.cas.command.CircuitSetCommand;
import l.circuit_redstone.logic.cas.command.CircuitTerminalCommand;
import l.circuit_redstone.logic.cas.debug.ChartSaver;
import l.circuit_redstone.logic.cas.debug.ChartSession;
import l.circuit_redstone.logic.cas.sim.SimulationManager;
import l.circuit_redstone.logic.cas.update.CircuitUpdates;
import l.circuit_redstone.logic.cas.update.OrphanBlockEntityCleanup;

/**
 * CircuitRedstone 主入口。
 */
public class CircuitRedstone implements ModInitializer {
	public static final String MOD_ID = "circuit-redstone_logic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModRegistry.init();
		CircuitUpdates.init();
		OrphanBlockEntityCleanup.init();
		SimulationManager.init();
		CircuitTerminalCommand.init();
		CircuitDebugCommand.init();
		CircuitSetCommand.init();
		CircuitReadCommand.init();
		ChartSession.init();
		// 一次启动日志：把"本模组的配方到底加载了几条"写清楚。
		// 配方属于数据包资源，加载失败时游戏只会安静地少几条配方，没有这行很难发现。
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int ours = 0;
			int total = 0;
			for (var entry : server.getRecipeManager().values()) {
				total++;
				if (MOD_ID.equals(entry.id().getNamespace())) {
					ours++;
				}
			}
			LOGGER.info("[{}] recipes loaded: {} from this mod ({} total)", MOD_ID, ours, total);
		});
		LOGGER.info("[{}] initialized with {} blocks", MOD_ID, ModRegistry.blockCount());
		// 运行图的"另存为"用 AWT 还是游戏内界面，取决于这个 JVM 是不是 headless；启动时打一行方便排查。
		LOGGER.info("[{}] chart save environment: {}", MOD_ID, ChartSaver.environmentDescription());
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
